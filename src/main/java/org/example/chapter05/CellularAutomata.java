package org.example.chapter05;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

/**
 * Listing 5.15 - Coordinating Computation in a Cellular Automaton with CyclicBarrier.
 *
 * Partitions a 2D board into NCPU sub-boards, one worker thread per
 * partition. Each worker computes new values for its sub-board, then
 * calls barrier.await(). When all workers have arrived, the BARRIER
 * ACTION (passed to the CyclicBarrier constructor) runs on one of
 * the worker threads and commits the new values; then all workers are
 * released to compute the next step.
 *
 * For CPU-bound work with no I/O and no shared data, NCPU (or NCPU+1)
 * threads give optimal throughput — more threads just compete for CPU.
 *
 * The Board class is simplified for demonstration; the book leaves its
 * details out. This implementation runs Conway's Game of Life-style
 * steps for a fixed number of iterations to keep the test deterministic.
 */
public class CellularAutomata {

    private final Board mainBoard;
    private final CyclicBarrier barrier;
    private final Worker[] workers;
    private final CountDownLatch done;

    public CellularAutomata(Board board) {
        this.mainBoard = board;
        int count = Runtime.getRuntime().availableProcessors();
        this.done = new CountDownLatch(count);
        this.barrier = new CyclicBarrier(count, mainBoard::commitNewValues);
        this.workers = new Worker[count];
        for (int i = 0; i < count; i++) {
            workers[i] = new Worker(mainBoard.getSubBoard(count, i));
        }
    }

    public void start() {
        for (Worker w : workers) {
            new Thread(w).start();
        }
    }

    public void waitForConvergence() throws InterruptedException {
        done.await();
    }

    private class Worker implements Runnable {
        private final Board board;

        Worker(Board board) {
            this.board = board;
        }

        @Override
        public void run() {
            try {
                while (!board.hasConverged()) {
                    for (int x = 0; x < board.getMaxX(); x++) {
                        for (int y = 0; y < board.getMaxY(); y++) {
                            board.setNewValue(x, y, computeValue(x, y));
                        }
                    }
                    barrier.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (BrokenBarrierException e) {
                // barrier broken — exit
            } finally {
                done.countDown();
            }
        }

        private int computeValue(int x, int y) {
            // Toy rule: increment the cell by 1 each step, capping at a bound.
            // In a real CA (e.g. Life), this would examine neighbors.
            return board.getValue(x, y) + 1;
        }
    }

    /**
     * Minimal Board abstraction: a rectangular integer grid with a
     * "new values" shadow buffer that is promoted to the main grid
     * via commitNewValues() — called by the barrier action between
     * steps, so all workers see a consistent state.
     */
    public static class Board {
        private final int[][] values;
        private final int[][] newValues;
        private final int maxSteps;
        private volatile int step;

        public Board(int width, int height, int maxSteps) {
            this.values = new int[width][height];
            this.newValues = new int[width][height];
            this.maxSteps = maxSteps;
        }

        public int getMaxX() { return values.length; }
        public int getMaxY() { return values[0].length; }
        public int getValue(int x, int y) { return values[x][y]; }

        public void setNewValue(int x, int y, int v) {
            newValues[x][y] = v;
        }

        public boolean hasConverged() {
            return step >= maxSteps;
        }

        public void commitNewValues() {
            for (int x = 0; x < getMaxX(); x++) {
                System.arraycopy(newValues[x], 0, values[x], 0, getMaxY());
            }
            step++;
        }

        public int getStep() { return step; }

        /**
         * Returns a view restricted to sub-board `index` of `parts`.
         * This trivial version returns the whole board; a real
         * partitioning would slice the array. Kept simple because
         * the point here is the synchronizer pattern, not the
         * partitioning math.
         */
        public Board getSubBoard(int parts, int index) {
            return this;
        }
    }
}
