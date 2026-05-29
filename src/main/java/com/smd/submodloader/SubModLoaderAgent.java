package com.smd.submodloader;

import java.lang.instrument.Instrumentation;

public class SubModLoaderAgent {

    public static void premain(String args, Instrumentation inst) {
        System.out.println("[SubModLoader Agent] Activated. Registering transformer...");
        inst.addTransformer(new SubModLoaderTransformer(), true);
    }
}
