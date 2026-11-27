package uk.co.reecenicholls.spawnticks.runtime;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

/**
 * Verifies SpawnTicks Mixin injectors actually wove into target methods (or still have
 * their MixinExtras late-injection target intact) after application, allowing graceful
 * degradation instead of a startup crash if another mod overhauls chunk ticking.
 */
public class SpawnTicksMixinPlugin implements IMixinConfigPlugin {
    private static final String MIXIN_MERGED_DESC = Type.getDescriptor(MixinMerged.class);
    //? if <1.21.2 {
    private static final String DISTANCE_MANAGER_OWNER = "net/minecraft/server/level/DistanceManager";
    private static final String SHOULD_FORCE_TICKS_NAME = "shouldForceTicks";
    private static final String SHOULD_FORCE_TICKS_DESC = "(J)Z";
    //?}

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        if (SpawnTicksRuntimeState.SERVER_CHUNK_CACHE_MIXIN.equals(mixinClassName)) {
            //? if >=1.21.2 {
            /*verifyHook(
                targetClass,
                true,
                "tickChunks",
                "(Lnet/minecraft/util/profiling/ProfilerFiller;JLjava/util/List;)V",
                "ServerChunkCache#tickChunks",
                SpawnTicksRuntimeState.CHUNK_TICK_HANDLER_FRAGMENT,
                false,
                "ServerChunkCache#tickChunks TAIL injector did not apply"
            );*/
            //?} else {
            verifyHook(
                targetClass,
                true,
                "tickChunks",
                "()V",
                "ServerChunkCache#tickChunks",
                SpawnTicksRuntimeState.CHUNK_TICK_HANDLER_FRAGMENT,
                true,
                "DistanceManager#shouldForceTicks injection target missing in ServerChunkCache#tickChunks"
            );
            //?}
        } else if (SpawnTicksRuntimeState.SERVER_LEVEL_MIXIN.equals(mixinClassName)) {
            verifyHook(
                targetClass,
                false,
                "tick",
                "(Ljava/util/function/BooleanSupplier;)V",
                "ServerLevel#tick",
                SpawnTicksRuntimeState.LEVEL_TICK_HANDLER_FRAGMENT,
                false,
                "ServerLevel#tick HEAD injector did not apply"
            );
        }
    }

    private static void verifyHook(
        ClassNode targetClass,
        boolean isChunkHook,
        String methodName,
        String methodDesc,
        String readableTarget,
        String handlerFragment,
        boolean expectWrapShouldForceTicks,
        String missingHookReason
    ) {
        MethodNode targetMethod = findMethod(targetClass, methodName, methodDesc);
        boolean foreignOverwrite = hasForeignMixinMerged(targetMethod);
        boolean hooked = targetMethod != null
            && !foreignOverwrite
            && (containsMethodCallName(targetMethod, handlerFragment)
                || (expectWrapShouldForceTicks
                    && hasShouldForceTicksCall(targetMethod)));

        Set<String> foreignMixins = hooked ? Set.of() : collectForeignMixins(targetClass, targetMethod);
        String reason = null;
        if (!hooked) {
            if (targetMethod == null) {
                reason = readableTarget + methodDesc + " was not found";
            } else if (foreignOverwrite) {
                reason = readableTarget + " was overwritten by another mod";
            } else {
                reason = missingHookReason;
            }
        }
        SpawnTicksRuntimeState.recordHookCheck(isChunkHook, hooked, foreignMixins, reason);
    }

    private static boolean hasShouldForceTicksCall(MethodNode method) {
        //? if <1.21.2 {
        return containsMethodInsn(
            method,
            DISTANCE_MANAGER_OWNER,
            SHOULD_FORCE_TICKS_NAME,
            SHOULD_FORCE_TICKS_DESC
        );
        //?} else {
        /*return false;*/
        //?}
    }

    private static MethodNode findMethod(ClassNode targetClass, String name, String desc) {
        for (MethodNode method : targetClass.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static boolean hasForeignMixinMerged(MethodNode method) {
        if (method == null) {
            return false;
        }
        Set<String> merged = new LinkedHashSet<>();
        collectForeignMixinsFromMethod(method, merged);
        return !merged.isEmpty();
    }

    private static boolean containsMethodCallName(MethodNode method, String nameSubstring) {
        if (method == null || method.instructions == null) {
            return false;
        }
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.contains(nameSubstring)) {
                return true;
            }
        }
        return false;
    }

    //? if <1.21.2 {
    private static boolean containsMethodInsn(
        MethodNode method,
        String owner,
        String name,
        String desc
    ) {
        if (method == null || method.instructions == null) {
            return false;
        }
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof MethodInsnNode call
                    && owner.equals(call.owner)
                    && name.equals(call.name)
                    && desc.equals(call.desc)) {
                return true;
            }
        }
        return false;
    }
    //?}

    private static Set<String> collectForeignMixins(ClassNode targetClass, MethodNode targetMethod) {
        Set<String> directMixins = new LinkedHashSet<>();
        collectForeignMixinsFromMethod(targetMethod, directMixins);
        if (!directMixins.isEmpty()) {
            return directMixins;
        }

        if (targetMethod != null) {
            Set<String> invokedMixins = collectForeignMixinsInvokedByMethod(targetClass, targetMethod);
            if (!invokedMixins.isEmpty()) {
                return invokedMixins;
            }
            return Set.of();
        }

        // Fall back to class-wide @MixinMerged scan only when the target method itself vanished.
        Set<String> classMixins = new LinkedHashSet<>();
        for (MethodNode method : targetClass.methods) {
            collectForeignMixinsFromMethod(method, classMixins);
        }
        return classMixins;
    }

    private static Set<String> collectForeignMixinsInvokedByMethod(ClassNode targetClass, MethodNode targetMethod) {
        if (targetMethod.instructions == null) {
            return Set.of();
        }
        Map<String, MethodNode> methodsByKey = new HashMap<>(targetClass.methods.size() * 2);
        for (MethodNode method : targetClass.methods) {
            methodsByKey.put(method.name + method.desc, method);
        }

        Set<String> invokedMixins = new LinkedHashSet<>();
        for (AbstractInsnNode insn : targetMethod.instructions) {
            if (insn instanceof MethodInsnNode call && targetClass.name.equals(call.owner)) {
                collectForeignMixinsFromMethod(methodsByKey.get(call.name + call.desc), invokedMixins);
            } else if (insn instanceof InvokeDynamicInsnNode indy && indy.bsmArgs != null) {
                for (Object arg : indy.bsmArgs) {
                    if (arg instanceof Handle handle && targetClass.name.equals(handle.getOwner())) {
                        collectForeignMixinsFromMethod(
                            methodsByKey.get(handle.getName() + handle.getDesc()),
                            invokedMixins
                        );
                    }
                }
            }
        }
        return invokedMixins;
    }

    private static void collectForeignMixinsFromMethod(MethodNode method, Set<String> outMixins) {
        if (method == null || method.visibleAnnotations == null) {
            return;
        }
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (!MIXIN_MERGED_DESC.equals(annotation.desc) || annotation.values == null) {
                continue;
            }
            for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
                if ("mixin".equals(annotation.values.get(i)) && annotation.values.get(i + 1) instanceof String mixinName) {
                    if (SpawnTicksRuntimeState.isForeignMixin(mixinName)) {
                        outMixins.add(mixinName);
                    }
                }
            }
        }
    }
}
