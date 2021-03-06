package de.saschapeukert.Database;

import de.saschapeukert.StartComparison;
import org.neo4j.cursor.Cursor;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.api.DataWriteOperations;
import org.neo4j.kernel.api.ReadOperations;
import org.neo4j.kernel.api.cursor.RelationshipItem;
import org.neo4j.kernel.api.exceptions.EntityNotFoundException;
import org.neo4j.kernel.api.exceptions.InvalidTransactionTypeKernelException;
import org.neo4j.kernel.api.exceptions.schema.ConstraintValidationKernelException;
import org.neo4j.kernel.api.properties.Property;
import org.neo4j.kernel.impl.api.store.RelationshipIterator;
import org.neo4j.kernel.impl.core.ThreadToStatementContextBridge;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.tooling.GlobalGraphOperations;

import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by Sascha Peukert on 31.08.2015.
 */
@SuppressWarnings("deprecation")
public class DBUtils {


    private static NeoStores neoStore;
    private static GraphDatabaseService graphDb;
    public int highestNodeKey;

    private static DBUtils instance;
    private final ThreadToStatementContextBridge ctx;
    //private static ReadOperations ops;

    public Node getSomeRandomNode( ThreadLocalRandom random){
        long r;
        while(true) {

            try {

                // NEW VERSION, checks Map for ID and not DB
                r = (long) random.nextInt(highestNodeKey);
                if(StartComparison.resultCounterContainsKey(r)){
                    return graphDb.getNodeById(r);
                }

            } catch (NotFoundException e){
                // NEW: this should never be happening!
                System.out.println("Something terrible is happend");
            }

        }
    }

    public ResourceIterator<Node> getResourceIteratorOfAllNodes(){
        GlobalGraphOperations ggop = GlobalGraphOperations.at(graphDb);
        return ggop.getAllNodes().iterator();
    }


    public ReadOperations getReadOperations(){

//        if(ops==null){
//            ThreadToStatementContextBridge ctx = ((GraphDatabaseAPI) graphDb).getDependencyResolver().resolveDependency(ThreadToStatementContextBridge.class);
//            ops = ctx.get().readOperations();
//        }
//        return ops;

        return ctx.get().readOperations();

    }

    public DataWriteOperations getDataWriteOperations(){
        ThreadToStatementContextBridge ctx =((GraphDatabaseAPI) graphDb).getDependencyResolver().resolveDependency(ThreadToStatementContextBridge.class); // TODO MOVE
        try {
            return ctx.get().dataWriteOperations();
        } catch (InvalidTransactionTypeKernelException e) {
            e.printStackTrace();
        }
        return null;
    }

    public long getSomeRandomNodeId(ThreadLocalRandom random){
        long r;
        while(true) {

            r = random.nextLong(highestNodeKey);

            // NEW VERSION without DB-Lookup
            if(StartComparison.resultCounterContainsKey(r))
                return r;

        }

    }

    public Relationship getSomeRandomRelationship(ThreadLocalRandom random, int highestNodeId){
        long r;
        while(true) {

            try {
                r = (long) random.nextInt(highestNodeId);
                Node n = graphDb.getNodeById(r);  // meh?
                return n.getRelationships(Direction.BOTH).iterator().next();
            } catch (NotFoundException | NoSuchElementException ex){
                // NEXT!
            }

        }

    }

    public boolean removePropertyFromAllNodes(int PropertyID, DataWriteOperations ops){
        //int i=0;
        Iterator<Node> it = getIteratorForAllNodes();
        try {
            while(it.hasNext()){
                ops.nodeRemoveProperty(it.next().getId(),PropertyID);
                //i++;
                //if(i%250000==0)
                //    System.out.println(i);
            }


        } catch (EntityNotFoundException e) {
            e.printStackTrace(); // TODO REMOVE
            return false;
        }

        return true;
    }

    /**
     *  ONLY REMOVES THE KEY!
     *  You have to remove the Property from every node too
     * @param propertyID
     * @param ops
     */
    public void removePropertyKey(int propertyID, DataWriteOperations ops){
        ops.graphRemoveProperty(propertyID);

    }

    public boolean createStringPropertyAtNode(long nodeID, String value, int PropertyID, DataWriteOperations ops){

        try {
            ops.nodeSetProperty(nodeID, Property.stringProperty(PropertyID, value));
        } catch (EntityNotFoundException | ConstraintValidationKernelException e) {
            e.printStackTrace(); // TODO REMOVE
            return false;
        }

        return true;
    }

    public boolean createIntPropertyAtNode(long nodeID, int value, int PropertyID, DataWriteOperations ops){

        try {
            ops.nodeSetProperty(nodeID, Property.intProperty(PropertyID, value));
        } catch (EntityNotFoundException | ConstraintValidationKernelException e) {
            e.printStackTrace(); // TODO REMOVE
            return false;
        }

        return true;
    }

    private int getHighestNodeID(){

        return (int) getStoreAcess().getNodeStore().getHighId();

    }

    private long getNextPropertyID(){
        return getStoreAcess().getPropertyStore().nextId();
    }

    private NeoStores getStoreAcess(){
        if(neoStore==null)
            neoStore = ((GraphDatabaseAPI)graphDb).getDependencyResolver().resolveDependency( NeoStores.class );
        return neoStore;
    }

    public  ResourceIterator<Node> getIteratorForAllNodes( ) {
        GlobalGraphOperations ggo = GlobalGraphOperations.at(graphDb);

        ResourceIterable<Node> allNodes = ggo.getAllNodes();
        return allNodes.iterator();

    }

    /**
     * Gets the PropertyID for a given PropertyName or creates a new ID for that name and returns it.
     * @param propertyName HAS TO BE UNIQUE
     * @return -1 if error happend
     */
    public int GetPropertyID(String propertyName){
        try(Transaction tx = graphDb.beginTx()) {

            DataWriteOperations ops = ctx.get().dataWriteOperations();

            return  ops.propertyKeyGetOrCreateForName(propertyName);

        } catch (Exception e) {
            e.printStackTrace();  // TODO REMOVE
        }

        return -1; // ERROR happend

    }


    public Set<Long> getConnectedNodeIDs(ReadOperations ops, long nodeID, Direction dir){
        Set<Long> it = new HashSet<>(100000);
        try {
            RelationshipIterator itR = ops.nodeGetRelationships(nodeID, dir);

            while(itR.hasNext()){
                long rID = itR.next();

                Cursor<RelationshipItem> relCursor = ops.relationshipCursor(rID);

                while(relCursor.next()){

                    RelationshipItem item = relCursor.get();
                    it.add(item.otherNode(nodeID));
                }
                relCursor.close();
            }

        } catch (EntityNotFoundException e) {
            e.printStackTrace();
        }

        return it;
    }

    public Transaction openTransaction(){
        return graphDb.beginTx();

    }

    public void closeTransactionWithSuccess(Transaction tx){
        tx.success();
        tx.close();
    }

    /**
     *  The constructor
     * @param path
     * @param pagecache
     */
    private DBUtils(String path, String pagecache){
        graphDb = new GraphDatabaseFactory()
                .newEmbeddedDatabaseBuilder(new File(path))
                .setConfig(GraphDatabaseSettings.pagecache_memory, pagecache)
                .setConfig(GraphDatabaseSettings.keep_logical_logs, "false")  // to get rid of all those neostore.trasaction.db ... files
                .setConfig(GraphDatabaseSettings.allow_store_upgrade, "true")
                .newGraphDatabase();

        ctx = ((GraphDatabaseAPI) graphDb).getDependencyResolver().resolveDependency(ThreadToStatementContextBridge.class);
        registerShutdownHook();

        highestNodeKey = getHighestNodeID();
    }

    /**
     * This will get you an instance of DBUtils. Only the first call has to be with usefull parameters
     * @param path
     * @param pagecache
     * @return
     */
    public static DBUtils getInstance(String path, String pagecache) {
        if(instance==null){
             instance = new DBUtils(path, pagecache);
        }

        return instance;

    }

    private void registerShutdownHook( )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.out.println("Shutting down neo4j.");
                try {
                    graphDb.shutdown();
                } catch (Exception e) {
                    e.printStackTrace();
                    graphDb.shutdown();
                } finally {
                    System.out.println("Shutting down neo4j complete.");
                }

            }
        });
    }

    public Result executeQuery(String query){
        return graphDb.execute(query);
    }
}
