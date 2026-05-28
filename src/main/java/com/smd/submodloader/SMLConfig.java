package com.smd.submodloader;


import com.cleanroommc.configanytime.ConfigAnytime;
import net.minecraftforge.common.config.Config;

@Config(modid = "submodloader")
public class SMLConfig {
    /**递归深度，范围 0~5*/
    public static int recurseDepth = 3;
    /**忽略关键词列表（文件夹名包含这些词就会被跳过）*/
    public static String[] ignoreKeywords = {"ignore", "unstable", "disable"};
    static {
        ConfigAnytime.register(SMLConfig.class);
    }
}