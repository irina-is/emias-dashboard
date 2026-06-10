package com.emias.dashboard.model;

public class PcrMoRow {

    public final String name;
    public final long   total;
    public final long   inTreatment;
    public final long   completed;
    public final long   interrupted;
    public final long   inQueue;
    public final long   uvo12Done;

    public PcrMoRow(String name, long total, long inTreatment, long completed,
                    long interrupted, long inQueue, long uvo12Done) {
        this.name        = name;
        this.total       = total;
        this.inTreatment = inTreatment;
        this.completed   = completed;
        this.interrupted = interrupted;
        this.inQueue     = inQueue;
        this.uvo12Done   = uvo12Done;
    }
}
