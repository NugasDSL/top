package top.server;

import com.google.protobuf.ByteString;

import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import toy.blockchain.Blockchain;
import toy.config.Node;
import toy.crypto.DigestMethod;
import toy.das.atomicBroadcast.RBrodcastService;
import toy.das.wrb.WrbNode;
import toy.proto.Types;
import toy.proto.BlockchainServiceGrpc;
import toy.servers.CTServer;
import toy.servers.Server;
import toy.servers.Statistics;
import toy.servers.ToyServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;

public class Top implements Server {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(Top.class);

    private WrbNode rmf;
    private final HashMap<Types.txID, Integer> txMap = new HashMap<>();
    private RBrodcastService deliverFork;
    private RBrodcastService sync;
    private int n;
    private int gcCount = 0;
    private int gcLimit = 1;
    private ToyServer[] group;
    private int[][] lastDelivered;
    private int[] lastGCpoint;
    private final Blockchain bc;
    private int id;
    private int c;
    private AtomicBoolean stopped = new AtomicBoolean(false);
    private Statistics sts = new Statistics();
    private Thread deliverThread = new Thread(() -> {
        try {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
            deliverFromGroup();
        } catch (InterruptedException e) {
            logger.debug(format("G-%d interrupted while delivering from group", id));
        }
    });
    private io.grpc.Server txsServer;
    private ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    private EventLoopGroup gnio = new NioEventLoopGroup(2);
    private int listenerPort;

    public Top(String addr, int listenerPort, int wrbPort, int id, int f, int c, int tmo, int tmoInterval
               , int maxTx, boolean fastMode, ArrayList<Node> cluster, String bbcConfig, String panicConfig
               , String syncConfig, String serverCrt, String serverPrivKey, String caRoot) {
        n = 3 *f +1;
        this.listenerPort = listenerPort;
        lastDelivered = new int[c][];
        lastGCpoint = new int[c];
        for (int i = 0 ; i < c ; i++) {
            lastDelivered[i] = new int[n];
            lastGCpoint[i] = 1;
            for (int j = 0 ; j < n ; j++) {
                lastDelivered[i][j] = 0;

            }
        }
        this.c = c;
        this.group = new ToyServer[c];
        this.id = id;
        rmf = new WrbNode(c, id, addr, wrbPort, f, tmo, tmoInterval, cluster, bbcConfig, serverCrt, serverPrivKey, caRoot);
        deliverFork = new RBrodcastService(c, id, panicConfig);
        sync = new RBrodcastService(c, id, syncConfig);
        for (int i = 0 ; i < c ; i++) {
            group[i] = new CTServer(addr, wrbPort, id, i, f, maxTx,
                    fastMode, rmf, deliverFork, sync);
        }
        bc = group[0].initBC(id, -1);
    }

    private void deliverFromGroup() throws InterruptedException {
        int currChannel = 0;
        int currBlock = 0;
        while (!stopped.get()) {
            for (currChannel = 0 ; currChannel < c ; currChannel++) {
                long start = System.currentTimeMillis();
                logger.debug(format("Trying to deliver from [channel=%d, channelBlock=%d]", currChannel, currBlock));
                Types.Block cBlock = group[currChannel].deliver(currBlock);
                sts.all++;
                sts.deliveredTime += System.currentTimeMillis() - start;
                gc(cBlock.getHeader().getHeight(), cBlock.getHeader().getM().getSender(), currChannel);
                if (cBlock.getDataCount() == 0) {
                    sts.eb++;
                    logger.info(format("E - [[time=%d], [height=%d], [sender=%d], [channel=%d], [size=0]]",
                            System.currentTimeMillis() - start, cBlock.getHeader().getHeight(),
                            cBlock.getHeader().getM().getSender(), cBlock.getHeader().getM().getChannel()));
                    continue;
                }
                cBlock = cBlock.toBuilder()
                        .setHeader(cBlock.getHeader().toBuilder()
                            .setHeight(bc.getHeight() + 1)
                            .setPrev(ByteString.copyFrom(
                                DigestMethod.hash(bc.getBlock(bc.getHeight()).getHeader().toByteArray())))
                            .build())
                            .setSt(cBlock.getSt().toBuilder().setDecided(System.currentTimeMillis()))
                        .build();
                synchronized (bc) {
                    bc.addBlock(cBlock);
                    bc.notify();
                }
                updateStat(cBlock);
                logger.info(format("F - [[time=%d], [height=%d], [sender=%d], [channel=%d], [size=%d]]",
                        System.currentTimeMillis() - start, cBlock.getHeader().getHeight(),
                        cBlock.getHeader().getM().getSender(), cBlock.getHeader().getM().getChannel(), cBlock.getDataCount()));
            }

            currBlock++;
        }
    }

    void updateStat(Types.Block b) {
        if (b.getHeader().getHeight() == 1) {
            sts.firstTxTs = b.getSt().getDecided();
            sts.txSize = b.getData(0).getSerializedSize();
        }
        sts.lastTxTs = max(sts.lastTxTs, b.getSt().getDecided());
        sts.txCount += b.getDataCount();
        for (Types.Transaction t : b.getDataList()) {
            txMap.put(t.getId(), b.getHeader().getHeight());
        }
        synchronized (txMap) {
            txMap.notifyAll();
        }
    }

    public Statistics getStatistics() {
        sts.totalDec = rmf.getTotolDec();
        sts.optemisticDec = rmf.getOptemisticDec();
        return sts;
    }

    void gc(int origHeight, int sender, int channel) {
        if (sender < 0 || sender > n - 1 || channel < 0 || channel > c -1 || origHeight < 0) {
            logger.debug(format("G-%d GC invalid argument [OrigHeight=%d ; sender=%d ; channel=%d]"
                    ,id, origHeight, sender, channel));
            return;
        }
        lastDelivered[channel][sender] = origHeight;
        gcCount++;
        if (gcCount < gcLimit) return;
        gcCount = 0;
        for (int i = 0 ; i < c ; i++) {
            gcForChannel(i);
        }
    }
    void gcForChannel(int channel) {
        int minHeight = lastDelivered[channel][0];
        for (int i = 0 ; i < n ; i++) {
            minHeight = min(minHeight, lastDelivered[channel][i]);
        }
        logger.debug(format("G-%d starting GC [OrigHeight=%d ; lastGCPoint=%d ;" +
                " channel=%d]",id, minHeight, lastGCpoint[channel], channel));
        for (int i = lastGCpoint[channel] ; i < minHeight ; i++) {
            group[channel].gc(i);
        }
        lastGCpoint[channel] = minHeight;

    }
    public void start() {
        try {
            txsServer = NettyServerBuilder
                    .forPort(listenerPort)
                    .executor(executor)
                    .bossEventLoopGroup(gnio)
                    .workerEventLoopGroup(gnio)
                    .addService(new txServer(this))
                    .build()
                    .start();
            logger.info("starting tx Server");
        } catch (IOException e) {
            logger.error("", e);
        }
        CountDownLatch latch = new CountDownLatch(3);
        new Thread(() -> {
            this.rmf.start();
            latch.countDown();
        }).run();
        new Thread(() -> {
            this.deliverFork.start();
            latch.countDown();
        }).run();
        new Thread(() -> {
            this.sync.start();
            latch.countDown();
        }).run();
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("", e);
            shutdown();
            return;
        }
        for (int i = 0 ; i < c ; i++) {
            group[i].start(true);
        }
    }

    public void shutdown() {
        stopped.set(true);
        deliverThread.interrupt();
        try {
            deliverThread.join();
        } catch (InterruptedException e) {
            logger.error(format("G-%d", id), e);
        }
        for (int i = 0 ; i < c ; i++) {
            group[i].shutdown(true);
            logger.debug(format("G-%d shutdown channel %d", id, i));
        }
        logger.debug(format("G-%d shutdown deliverThread", id));
        rmf.stop();
        logger.debug(format("G-%d shutdown wrb Service", id));
        deliverFork.shutdown();
        logger.debug(format("G-%d shutdown panic service", id));
        sync.shutdown();
        logger.debug(format("G-%d shutdown sync service", id));
        txsServer.shutdown();
    }

    public void serve() {
        for (int i = 0 ; i < c ; i++) {
            group[i].serve();
        }
        deliverThread.start();
    }

    @Override
    public Types.txID addTransaction(Types.Transaction tx) {
        int ps = group[0].getTxPoolSize();
        int chan = 0;
        for (int i = 1 ; i < c ; i++) {
            int cps = group[i].getTxPoolSize();
            if (ps > cps) {
                ps = cps;
                chan = i;
            }
        }
        Types.Transaction ntx = tx.toBuilder()
                .setServerTs(System.currentTimeMillis())
                .setId(Types.txID.newBuilder().setTxID(UUID.randomUUID().toString()))
                .build();
        return group[chan].addTransaction(ntx);
    }

    public int isTxPresent(String txID) {
        for (int i = 0 ; i < c ; i++) {
            int ret = group[i].isTxPresent(txID);
            if (ret != -1) return ret;
        }
        return -1;
    }

    Types.approved getTransaction(Types.txID txID) throws InterruptedException {
        Types.Block b = null;
        synchronized (txMap) {
            while (!txMap.containsKey(txID)) {
                txMap.wait();
            }
        }
        if (txMap.containsKey(txID)) {
            b = nonBlockingDeliver(txMap.get(txID));
        }
        if (b == null) return Types.approved.getDefaultInstance();
        for (Types.Transaction t : b.getDataList()) {
            if (t.getId().getTxID().equals(txID.getTxID())) {
                return Types.approved.newBuilder().setSt(b.getSt()).setTx(t).build();
            }
        }
        return Types.approved.getDefaultInstance();
    }

    public Types.Block deliver(int index) throws InterruptedException {
        synchronized (bc) {
            while (bc.getHeight() < index) {
                bc.wait();
            }
            Types.Block b = bc.getBlock(index);
            return b;
        }
    }

    public Types.Block nonBlockingDeliver(int index) {
        if (bc.getHeight() < index) return null;
        Types.Block b = bc.getBlock(index);
        return b;
    }

    @Override
    public int getID() {
        return id;
    }

    @Override
    public void setByzSetting(boolean fullByz, List<List<Integer>> groups) {
      /*
        Currently not supported
       */
    }

    @Override
    public void setAsyncParam(int maxTime) {
       /*
        Currently not supported
       */
    }

    @Override
    public int getBCSize() {
        return bc.getHeight() + 1;
    }

}

class txServer extends BlockchainServiceGrpc.BlockchainServiceImplBase {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(txServer.class);
    Top server;

    public txServer(Top server) {
        super();
        this.server = server;
    }
    @Override
    public void addTransaction(Types.Transaction request, StreamObserver<Types.accepted> responseObserver) {
        logger.info("add tx...");
        logger.info("receive write request");
        boolean ac = true;
        Types.txID id = server.addTransaction(request);
        if (id == null) ac = false;
        responseObserver.onNext(Types.accepted.newBuilder().setTxID(id).setAccepted(ac).build());
        responseObserver.onCompleted();
    }

    @Override
    public void getTransaction(Types.read request, StreamObserver<Types.approved> responseObserver) {
        logger.info("receive read request");
        try {
            responseObserver.onNext(server.getTransaction(request.getTxID()));
        } catch (InterruptedException e) {
            logger.error("", e);
        }
        responseObserver.onCompleted();
    }
}