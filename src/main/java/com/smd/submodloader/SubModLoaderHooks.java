package com.smd.submodloader;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.common.discovery.ContainerType;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.ModDiscoverer;
import net.minecraftforge.fml.relauncher.CoreModManager;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class SubModLoaderHooks {

    static final Logger LOGGER = LogManager.getLogger("SubModLoader");
    private static final String SUB_MODS_DIR_NAME = "mods/submods";
    private static final int RECURSION_DEPTH = 5; // 可根据需要提取为配置文件

    /**
     * 由 CoreModManager.discoverCoreMods() 末尾调用
     * 在 Forge 扫描完默认目录后，额外从子文件夹中加载 CoreMod
     */
    @SuppressWarnings("unused") // 被字节码调用
    public static void injectCoreMods() {
        try {
            File mcDir = (File) FMLInjectionData.data()[6]; // mcLocation
            File subModsDir = new File(mcDir, SUB_MODS_DIR_NAME);
            if (!subModsDir.isDirectory()) {
                return;
            }

            LaunchClassLoader classLoader = Launch.classLoader;
            Method loadCoreMod = CoreModManager.class.getDeclaredMethod(
                    "loadCoreMod", LaunchClassLoader.class, String.class, File.class);
            loadCoreMod.setAccessible(true);

            loadCoreModsFromDir(subModsDir, RECURSION_DEPTH, classLoader, loadCoreMod);
        } catch (Exception e) {
            LOGGER.error("[SubModLoader] Failed to inject CoreMods", e);
        }
    }

    /**
     * 由 ModDiscoverer.findClasspathMods() 开头调用
     * 为普通 @Mod 注解的 jar 提供候选
     */
    public static void injectModCandidates(ModDiscoverer discoverer, Object modClassLoader) {
        System.out.println("[SubModLoader] injectModCandidates called!");
        try {
            // 1. 获取游戏目录
            File mcDir = (File) FMLInjectionData.data()[6];
            File subModsDir = new File(mcDir, SUB_MODS_DIR_NAME); // 例如 "mods/submods"
            if (!subModsDir.isDirectory()) {
                System.err.println("[SubModLoader] submods directory not found: " + subModsDir);
                return;
            }

            // 2. 通过反射获取 ModDiscoverer 的 candidates 字段
            Field candidatesField = null;
            for (Field f : ModDiscoverer.class.getDeclaredFields()) {
                if (f.getType() == List.class || f.getType().isAssignableFrom(List.class)) {
                    candidatesField = f;
                    candidatesField.setAccessible(true);
                    break;
                }
            }
            if (candidatesField == null) {
                System.err.println("[SubModLoader] Could not find candidates field");
                return;
            }
            @SuppressWarnings("unchecked")
            List<ModCandidate> candidates = (List<ModCandidate>) candidatesField.get(discoverer);

            File[] jarFiles = subModsDir.listFiles((dir, name) -> name.endsWith(".jar"));
            if (jarFiles == null || jarFiles.length == 0) {
                System.out.println("[SubModLoader] No jars found in submods");
                return;
            }

            for (File jar : jarFiles) {
                ModCandidate candidate = new ModCandidate(
                        jar,                  // classPathRoot
                        jar,                  // modContainer
                        ContainerType.JAR,    // 类型
                        false,                // 不是 minecraft jar
                        false                 // 不是 classpath 注入
                );
                candidates.add(candidate);
                System.out.println("[SubModLoader] Added candidate: " + jar.getName());
            }

            System.out.println("[SubModLoader] Total candidates after injection: " + candidates.size());

        } catch (Exception e) {
            System.err.println("[SubModLoader] Exception in injectModCandidates:");
            e.printStackTrace();
        }
    }

    private static void loadCoreModsFromDir(File dir, int depth, LaunchClassLoader cl, Method loadCoreMod) throws Exception {
        if (depth < 0) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".jar")) {
                try (JarFile jar = new JarFile(file)) {
                    Manifest manif = jar.getManifest();
                    if (manif != null) {
                        String fmlCorePlugin = manif.getMainAttributes().getValue("FMLCorePlugin");
                        if (fmlCorePlugin != null && !fmlCorePlugin.isEmpty()) {
                            LOGGER.info("[SubModLoader] Injecting CoreMod {} from {}",
                                    fmlCorePlugin, file.getName());
                            loadCoreMod.invoke(null, cl, fmlCorePlugin, file);
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn("[SubModLoader] Error reading jar: {}", file.getName(), e);
                }
            } else if (file.isDirectory()) {
                loadCoreModsFromDir(file, depth - 1, cl, loadCoreMod);
            }
        }
    }
}
