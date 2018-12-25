package top.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import toy.proto.Types;
import toy.proto.blockchainServiceGrpc;

public class TopClient {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(TopClient.class);
    private blockchainServiceGrpc.blockchainServiceBlockingStub stub;
    private ManagedChannel channel;
    private int clientID;

    public TopClient(int clientID, String addr, int port) {
        this.clientID = clientID;
        channel = ManagedChannelBuilder.forAddress(addr, port).usePlaintext().build();
        stub = blockchainServiceGrpc.newBlockingStub(channel);

    }
    public int getID() {
        return clientID;
    }

    public Types.accepted addTx(byte[] data) {
        try {
            Types.Transaction t = Types.Transaction.newBuilder()
                    .setClientID(clientID)
                    .setData(ByteString.copyFrom(data))
                    .setClientTs(System.currentTimeMillis())
                    .build();
            return stub.addTransaction(t);
        } catch (Exception e) {
            logger.error("", e);
            return null;
        }
    }


    public Types.approved getTx(Types.read r) {
        return stub.getTransaction(r);
    }

    public void shutdown() {
        channel.shutdown();
    }
}
