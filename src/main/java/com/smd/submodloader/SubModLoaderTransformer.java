package com.smd.submodloader;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

public class SubModLoaderTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className,
                            Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                            byte[] classfileBuffer) {
        // 拦截 CoreModManager
        if ("net/minecraftforge/fml/relauncher/CoreModManager".equals(className)) {
            System.out.println("[SubModLoader Agent] Patching CoreModManager...");
            return patchCoreModManager(classfileBuffer);
        }
        // 拦截 ModDiscoverer
        if ("net/minecraftforge/fml/common/discovery/ModDiscoverer".equals(className)) {
            System.out.println("[SubModLoader Agent] Patching ModDiscoverer...");
            return patchModDiscoverer(classfileBuffer);
        }
        return null; // 不修改其他类
    }

    private byte[] patchCoreModManager(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                // 修改 discoverCoreMods 方法 (静态方法，无参数)
                if ("discoverCoreMods".equals(name) && "()V".equals(desc)) {
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        @Override
                        public void visitInsn(int opcode) {
                            // 在方法最后一条 RETURN 之前注入我们的逻辑
                            if (opcode == Opcodes.RETURN) {
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                        "com/smd/submodloader/SubModLoaderHooks",
                                        "injectCoreMods",
                                        "()V",
                                        false);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }

    private byte[] patchModDiscoverer(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        // 第一步：收集所有方法信息
        final List<String[]> methods = new ArrayList<>();
        cr.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                methods.add(new String[]{name, desc});
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        }, 0);

        // 打印出来
        SubModLoaderHooks.LOGGER.info("[SubModLoader Agent] ModDiscoverer methods:");
        for (String[] m : methods) {
            SubModLoaderHooks.LOGGER.info("  {} {}", m[0], m[1]);
        }

        // 第二步：重新读取并真正注入
        ClassReader cr2 = new ClassReader(bytes);
        cr2.accept(new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
                // 自动尝试几个常见名字
                if (name.equals("findClasspathMods") || name.equals("discoverMods") || name.equals("findModDirMods")) {
                    SubModLoaderHooks.LOGGER.info("[SubModLoader Agent] Patching method: {} {}", name, desc);
                    return injectAtStart(mv, access, name, desc);
                }
                // 如果方法名包含 "mods" 且没有其他明显特征，也可以考虑
                if (name.toLowerCase().contains("mods") && desc.equals("()V")) {
                    SubModLoaderHooks.LOGGER.info("[SubModLoader Agent] Patching potential method: {} {}", name, desc);
                    return injectAtStart(mv, access, name, desc);
                }
                return mv;
            }

            private MethodVisitor injectAtStart(MethodVisitor mv, int access, String name, String desc) {
                return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                    @Override
                    protected void onMethodEnter() {
                        // 注入 SubModLoaderHooks.injectModCandidates(ModDiscoverer, null)
                        mv.visitVarInsn(Opcodes.ALOAD, 0); // this
                        mv.visitInsn(Opcodes.ACONST_NULL); // modClassLoader (可能不需要)
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "com/smd/submodloader/SubModLoaderHooks",
                                "injectModCandidates",
                                "(Lnet/minecraftforge/fml/common/discovery/ModDiscoverer;Ljava/lang/Object;)V",
                                false);
                    }
                };
            }
        }, 0);
        return cw.toByteArray();
    }
}
