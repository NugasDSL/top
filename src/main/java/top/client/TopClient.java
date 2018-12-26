package top.client;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import toy.proto.Types;
import toy.proto.BlockchainServiceGrpc;

/**
 * This class implements a simple Top client
 */
public class TopClient {
    private final static org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(TopClient.class);
    private BlockchainServiceGrpc.BlockchainServiceBlockingStub stub;
    private ManagedChannel channel;
    private int clientID;

    /**
     * Constructor
     * @param clientID the client identifier
     * @param addr the server ip address
     * @param port the server port
     */
    public TopClient(int clientID, String addr, int port) {
        this.clientID = clientID;
        channel = ManagedChannelBuilder.forAddress(addr, port).usePlaintext().build();
        stub = BlockchainServiceGrpc.newBlockingStub(channel);

    }

    /**
     * Get the client ID
     * @return the client ID
     */
    public int getID() {
        return clientID;
    }

    /**
     * Submit transaction to the server. Due to Top implementation this method does not block.
     * @param data the transaction data
     * @return return an indication if the transaction accepted (and now waits for approval) or denied as well as the
     * transaction identifier (if accepted)
     */
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

    /**
     * Get an approved transaction
     * @param r contains the transaction identifier
     * @return an indication of the transaction state. If the transaction already approved then the return value
     * contains the transaction. Else it is a DefaultInstance
     */
    public Types.approved getTx(Types.read r) {
        return stub.getTransaction(r);
    }

    /**
     * Shutdown the client.
     */
    public void shutdown() {
        channel.shutdown();
    }
}
