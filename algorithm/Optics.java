package algorithm;

import java.io.*;
import java.util.*;
import java.lang.reflect.Array;

import model.*;
import algorithm.*;

/**
 * Implementation of OPTICS.
 */
public class Optics extends Algorithm
{

    /**
     * Seeds queue.
     *
     * This is a min-PriorityQueue.
     */
    PriorityQueue<AlgorithmPoint> seeds;

    /**
     * Map of points.
     */
    ArrayList<AlgorithmPoint> points;

    /**
     * Parameters.
     */
    double epsilon;
    int minPts;


    /**
     * Constructor.
     */
    public Optics()
    {
        super();
    }

    /**
     * Find the parameters
     *
     * @param ci Minimum number of clusters
     * @param cj Maximum number of clusters
     * @param n Number of points
     */
    public void findParameters(int ci, int cj, int n, long width, long height)
    {
        super.findParameters(ci, cj, n, width, height);

        // first the epsilon
        // Z = w + h
        // epsilon = Z / sqrt(n)
        double Z = width + height;

        epsilon = Z / Math.sqrt(n);
        //System.out.println("E: " + epsilon + " n: " + n + " Z: " + Z + " w: " + width + " h: " + height);

        // now the minPts. We want to make sure that this is not too big,
        // because that will decrease the running time fast
        // thus, we give it a maximum value of 40, also, a minimum of 8
        // We'll take the 2.5th root of (n / cj), and add 5
        minPts = (int) Math.pow(n / cj, 1.0/2.5) + 5;

        if (minPts < 8) {
            minPts = 8;
        } else if (minPts > 40) {
            minPts = 40;
        }
        //System.out.println("minPts: " + minPts + " n: " + n + " cj: " + cj + " n/cj: " + n / cj);

        // the minClusterSize is slightly different, making this bigger on
        // bigger datasets, will make the finding of clusters faster. But
        // making it too small on big datasets will make it a weird output.
        minClusterSize = (n/3) / cj;
        //System.out.println("minClusterSize: " + minClusterSize + " n / 3: " + n / 3+ " cj: " + cj + " (n / 3) / cj: " + (n / 3) / cj);

        if (DISTANCE_METRIC == Calculations.DISTANCE_EUCLIDIAN_SQ) {
            epsilon = epsilon * epsilon;
        }
    }

    public void run()
    {
        points = new ArrayList<AlgorithmPoint>();

        reachabilityPlot = new ArrayList<AlgorithmPoint>(field.size());

        for (Point p : field.getAllPoints()) {
            AlgorithmPoint op = new AlgorithmPoint(p);
            points.add(op);
        }

        points.addAll(createNoise());

        // initialize the Priority Queue
        seeds = new PriorityQueue<AlgorithmPoint>();


        for (AlgorithmPoint p : points) {
            if (p.isProcessed()) {
                continue;
            }

            expandClusterOrder(p);
        }

        cluster();
    }

    void expandClusterOrder(AlgorithmPoint p)
    {
        List<List<PrioPair<AlgorithmPoint,Double>>> N = getNeighbours(p);

        write(p);

        double coredist = coreDistance(N, p);

        if (coredist != UNDEFINED) {
            update(N, p, coredist);

            AlgorithmPoint q;

            while ((q = seeds.poll()) != null) {
                List<List<PrioPair<AlgorithmPoint,Double>>> N_ = getNeighbours(q);

                write(q);

                double coredist_ = coreDistance(N_, q);

                if (coredist_ != UNDEFINED) {
                    update(N_, q, coredist_);
                }
            }
        }
    }

    double coreDistance(List<List<PrioPair<AlgorithmPoint,Double>>> N, AlgorithmPoint p)
    {
        if (N.get(1).size() >= minPts) {
            return N.get(0).get(0).getV().doubleValue();
        }
        return UNDEFINED;
    }

    /**
     * O(n) finding of the neighbours.
     *
     * With R-trees, the running time should decrease to (expected) O(n log n)
     *
     * @return A list with two elements.
     *          - The first one contains the nearest neighbours. (size should be minPts)
     *          - The second one contains the epsilon-neighbourhood.
     */
    List<List<PrioPair<AlgorithmPoint,Double>>> getNeighbours(AlgorithmPoint p)
    {
        PriorityQueue<PrioPair<AlgorithmPoint,Double>> pq = new PriorityQueue<PrioPair<AlgorithmPoint,Double>>();

        List<PrioPair<AlgorithmPoint,Double>> epsilonRangeList = new ArrayList<PrioPair<AlgorithmPoint,Double>>();

        for (AlgorithmPoint q : points) {
            if (q == p) {
                continue;
            }

            double dist = Calculations.distance(p.getPoint(), q.getPoint(), DISTANCE_METRIC);

            PrioPair<AlgorithmPoint,Double> pair = new PrioPair<AlgorithmPoint,Double>(q, dist);

            if (dist <= epsilon) {
                epsilonRangeList.add(pair);
            }

            // add the pair
            if (pq.size() < minPts) {
                pq.add(pair);
            } else {
                if (dist < ((Double) pq.peek().getV())) {
                    // remove the highest element
                    pq.poll();
                    pq.add(pair);
                }
            }
        }

        List<PrioPair<AlgorithmPoint,Double>> nextNeighbours = new ArrayList<PrioPair<AlgorithmPoint,Double>>();
        PrioPair<AlgorithmPoint,Double> pair;

        // basically the last step of HeapSort
        while ((pair = pq.poll()) != null) {
            nextNeighbours.add(pair);
        }

        List<List<PrioPair<AlgorithmPoint,Double>>> result = new ArrayList<List<PrioPair<AlgorithmPoint,Double>>>();

        result.add(nextNeighbours);
        result.add(epsilonRangeList);

        return result;
    }

    /**
     * Update method
     */
    void update(List<List<PrioPair<AlgorithmPoint,Double>>> N, AlgorithmPoint p, double coredist)
    {
        //System.out.println("Eneigh: " + N.get(1).size());
        for (PrioPair<AlgorithmPoint,Double> pair : N.get(1)) {
            AlgorithmPoint o = pair.getT();
            if (!o.isProcessed() || o.getReachabilityDistance() == UNDEFINED) {
                double newReachabilityDistance = Math.max(coredist, Calculations.distance(o.getPoint(), p.getPoint(), DISTANCE_METRIC));

                if (o.getReachabilityDistance() == UNDEFINED) {
                    o.setReachabilityDistance(newReachabilityDistance);
                    seeds.add(o);
                } else {
                    if (newReachabilityDistance < o.getReachabilityDistance()) {
                        seeds.remove(o);
                        o.setReachabilityDistance(newReachabilityDistance);
                        seeds.add(o);
                    }
                }
            }
        }
    }

    /**
     * Write.
     */
    void write(AlgorithmPoint op)
    {
        if (!op.isProcessed()) {
            op.process();
            op.setX(reachabilityPlot.size());
            reachabilityPlot.add(op);
        }
    }

    class Pair<T,V>
    {
        T t;
        V v;

        public Pair(T t, V v)
        {
            this.t = t;
            this.v = v;
        }

        public T getT()
        {
            return t;
        }

        public V getV()
        {
            return v;
        }
    }

    class PrioPair<T,V extends Comparable<V>> implements Comparable<PrioPair<T,V>>
    {
        T t;
        V v;

        public PrioPair(T t, V v)
        {
            this.t = t;
            this.v = v;
        }

        public T getT()
        {
            return t;
        }

        public V getV()
        {
            return v;
        }

        public int compareTo(PrioPair<T,V> c)
        {
            // to reverse the priority queue, to a max-PriorityQueue
            return v.compareTo(c.getV()) * -1;
        }
    }
}
