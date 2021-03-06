/*
  Copyright 2006 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package sim.app.dheatbugsrma;
import sim.field.grid.*;
import sim.util.*;
import sim.engine.*;

import mpi.*;
import java.nio.*;

public class DHeatBugRMA implements Steppable {
    private static final long serialVersionUID = 1;

    public double idealTemp;
    public double getIdealTemperature() { return idealTemp; }
    public void setIdealTemperature( double t ) { idealTemp = t; }

    public double heatOutput;
    public double getHeatOutput() { return heatOutput; }
    public void setHeatOutput( double t ) { heatOutput = t; }

    public double randomMovementProbability;
    public double getRandomMovementProbability() { return randomMovementProbability; }
    public void setRandomMovementProbability( double t ) { if (t >= 0 && t <= 1) randomMovementProbability = t; }
    public Object domRandomMovementProbability() { return new Interval(0.0, 1.0); }

    public int loc_x, loc_y;
    public boolean isInit = true;

    public DHeatBugRMA( double idealTemp, double heatOutput, double randomMovementProbability, int loc_x, int loc_y) {
        this.heatOutput = heatOutput;
        this.idealTemp = idealTemp;
        this.randomMovementProbability = randomMovementProbability;
        this.loc_x = loc_x;
        this.loc_y = loc_y;
    }

    public void addHeat(final DDoubleGrid2DRMA grid, final int x, final int y, final double heat) {
        double new_heat = grid.get(x, y) + heat;
        if (new_heat > DHeatBugsRMA.MAX_HEAT)
            new_heat = DHeatBugsRMA.MAX_HEAT;
        grid.set(x, y, new_heat);
    }

    public void step( final SimState state ) {
        DHeatBugsRMA hb = (DHeatBugsRMA)state;

        int myx = loc_x;
        int myy = loc_y;

        if (!this.isInit) {
            addHeat(hb.valgrid, loc_x, loc_y, heatOutput);
        } else {
            this.isInit = false;
        }

        final int START = -1;
        int bestx = START;
        int besty = 0;

        if (state.random.nextBoolean(randomMovementProbability)) { // go to random place
            bestx = (state.random.nextInt(3) - 1 + loc_x) % hb.valgrid.width;  // toroidal
            besty = (state.random.nextInt(3) - 1 + loc_y) % hb.valgrid.height;  // toroidal
        } else if ( hb.valgrid.get(myx, myy) > idealTemp ) { // go to coldest place
            for (int x = -1; x < 2; x++)
                for (int y = -1; y < 2; y++)
                    if (!(x == 0 && y == 0)) {
                        int xx = (x + loc_x);
                        int yy = (y + loc_y);
                        if (bestx == START ||
                                (hb.valgrid.get(xx, yy) < hb.valgrid.get(bestx, besty)) ||
                                (hb.valgrid.get(xx, yy) == hb.valgrid.get(bestx, besty) && state.random.nextBoolean()))  // not uniform, but enough to break up the go-up-and-to-the-left syndrome
                        { bestx = xx; besty = yy; }
                    }
        } else if ( hb.valgrid.get(myx, myy) < idealTemp ) { // go to warmest place
            for (int x = -1; x < 2; x++)
                for (int y = -1; y < 2; y++)
                    if (!(x == 0 && y == 0)) {
                        int xx = (x + loc_x);
                        int yy = (y + loc_y);
                        if (bestx == START ||
                                (hb.valgrid.get(xx, yy) > hb.valgrid.get(bestx, besty)) ||
                                (hb.valgrid.get(xx, yy) == hb.valgrid.get(bestx, besty) && state.random.nextBoolean()))  // not uniform, but enough to break up the go-up-and-to-the-left syndrome
                        { bestx = xx; besty = yy; }
                    }
        } else {        // stay put
            bestx = loc_x;
            besty = loc_y;
        }

        bestx = bestx < 0 ? hb.valgrid.width + bestx : bestx % hb.valgrid.width;
        besty = besty < 0 ? hb.valgrid.height + besty : besty % hb.valgrid.height;

        move(hb, bestx, besty);
    }

    public void move(DHeatBugsRMA hb, int x, int y) {
        final int destRank = x / hb.cell_height * hb.p_h + y / hb.cell_width;

        IntBuffer ob = MPI.newIntBuffer(1).put(5);
        IntBuffer nb = MPI.newIntBuffer(1);
        DoubleBuffer mb = MPI.newDoubleBuffer(5).put(new double[] {this.idealTemp, this.heatOutput, this.randomMovementProbability, (double)x, (double)y});

        try {
            hb.schedAtomicWin.lock(MPI.LOCK_SHARED, destRank, 0);
            hb.schedAtomicWin.fetchAndOp(ob, nb, MPI.INT, destRank, 0, MPI.SUM);
            hb.schedAtomicWin.unlock(destRank);

            hb.schedWin.put(mb, 5, MPI.DOUBLE, destRank, nb.get(0), 5, MPI.DOUBLE);
        } catch (MPIException e) {
            e.printStackTrace(System.out);
            System.exit(-1);
        }
    }
}