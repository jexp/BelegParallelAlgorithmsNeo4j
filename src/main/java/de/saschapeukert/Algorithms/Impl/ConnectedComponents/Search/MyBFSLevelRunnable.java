package de.saschapeukert.Algorithms.Impl.ConnectedComponents.Search;

import de.saschapeukert.Algorithms.MyAlgorithmBaseRunnable;
import de.saschapeukert.StartComparison;
import org.neo4j.graphdb.Direction;
import org.neo4j.kernel.api.ReadOperations;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Sascha Peukert on 04.10.2015.
 */
public class MyBFSLevelRunnable extends MyAlgorithmBaseRunnable {

    public volatile long parentID;
    public Direction direction;
    //public volatile Set<Long> ignoreIDs;
    private ReadOperations ops;
    private int posInList;

    public AtomicBoolean isAlive = new AtomicBoolean(true);
    public AtomicBoolean isIdle = new AtomicBoolean(true);

    public MyBFSLevelRunnable(int pos, boolean output){
        super(output);
        //this.direction = direction;
        posInList = pos;
    }

    @Override
    protected void compute() {
        ops = db.getReadOperations();
        //System.out.println("Thread " + posInList + " alive");
        while (isAlive.get()) {

            //System.out.println("Thread " + posInList + " waiting");
            while(isIdle.get()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //System.out.println("Thread " + posInList + " active");
            if (!isAlive.get()) {
                //System.out.println("Thread " + posInList + " killed");
                return;
            }

            int i = 0; //counter
            while (true) {

                try {
                    int key = ((i * StartComparison.NUMBER_OF_THREADS) + posInList);
                    parentID = MyBFS.frontierList.get(key);
                    Set<Long> q = expandNode(parentID);

                    MyBFS.MapOfQueues.put(key, q);
                    MyBFS.visitedIDs.addAll(q);
                    //ignoreIDs.addAll(q); // !
                    //System.out.println("Done " + parentID + " (T" + posInList + ")");
                    i++;

                } catch (IndexOutOfBoundsException e) {
                    break;
                } catch (Exception e){
                    e.printStackTrace();
                }
            }

            MyBFS.setCheckThreadList(posInList, true);
            isIdle.set(true);
            //System.out.println("Thread " + posInList + " done");

        }
    }


    private Set<Long> expandNode(Long id){
        Set<Long> result = new HashSet<>(db.getConnectedNodeIDs(ops, id, direction));
        result.removeAll(MyBFS.visitedIDs);
        return result;
    }
}


