package com.smd.submodloader.mixin;


import com.smd.submodloader.SMLConfig;
import net.minecraftforge.fml.common.ModClassLoader;
import net.minecraftforge.fml.common.discovery.ContainerType;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.ModDiscoverer;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Mixin(value = ModDiscoverer.class, remap = false)
public class MixinModDiscoverer {

    @Inject(method = "findClasspathMods", at = @At("TAIL"))
    private void sml_scanSubfolders(ModClassLoader modClassLoader, CallbackInfo ci) {
        File modsDir = new File((File) FMLInjectionData.data()[6], "mods");
        if (!modsDir.isDirectory()) {
            return;
        }
        scanRecursive(modsDir, SMLConfig.recurseDepth, Arrays.asList(SMLConfig.ignoreKeywords));
    }

    private void scanRecursive(File dir, int depth, List<String> keywords) {
        if (depth < 0) {
            return;
        }
        if (keywords.stream().anyMatch(k -> dir.getName().contains(k))) {
            return;
        }

        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".jar")) {
                ((ModDiscoverer)(Object)this).addCandidate(
                        new ModCandidate(file, file, ContainerType.JAR, false, true)
                );
            } else if (file.isDirectory()) {
                scanRecursive(file, depth - 1, keywords);
            }
        }
    }
}