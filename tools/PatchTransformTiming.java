import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Patch only the two integer returns of the installed baseline's timing method. */
public class PatchTransformTiming {
    public static void main(String[] args) throws Exception {
        String entry="com/dbzenith/transform/FormHandler.class";
        byte[] original;
        try(ZipFile jar=new ZipFile(args[0])) { original=jar.getInputStream(jar.getEntry(entry)).readAllBytes(); }
        ClassReader reader=new ClassReader(original);
        ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
        int[] methods={0},returns={0},entries={0};
        reader.accept(new ClassVisitor(Opcodes.ASM9,writer) {
            @Override public MethodVisitor visitMethod(int access,String name,String desc,String signature,String[] exceptions) {
                MethodVisitor delegate=super.visitMethod(access,name,desc,signature,exceptions);
                if(name.equals("enter")&&desc.equals("(Lnet/minecraft/server/level/ServerPlayer;Lcom/dbzenith/data/PlayerData;Lcom/dbzenith/transform/Form;)V")) {
                    return new MethodVisitor(Opcodes.ASM9,delegate) {
                        @Override public void visitInsn(int opcode) {
                            if(opcode==Opcodes.RETURN) {
                                super.visitVarInsn(Opcodes.ALOAD,1);super.visitVarInsn(Opcodes.ALOAD,2);
                                super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/dbzenith/transform/TransformationExperience","completed","(Lcom/dbzenith/data/PlayerData;Lcom/dbzenith/transform/Form;)V",false);entries[0]++;
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                if(!name.equals("transformTime")||!desc.equals("(Lcom/dbzenith/data/PlayerData;Lcom/dbzenith/transform/Form;)I")) return delegate;
                methods[0]++;
                return new MethodVisitor(Opcodes.ASM9,delegate) {
                    @Override public void visitInsn(int opcode) {
                        if(opcode==Opcodes.IRETURN) { super.visitVarInsn(Opcodes.ALOAD,0);super.visitVarInsn(Opcodes.ALOAD,1);super.visitMethodInsn(Opcodes.INVOKESTATIC,"com/dbzenith/transform/TransformationExperience","duration","(ILcom/dbzenith/data/PlayerData;Lcom/dbzenith/transform/Form;)I",false);returns[0]++; }
                        super.visitInsn(opcode);
                    }
                };
            }
        },0);
        if(methods[0]!=1||returns[0]!=2||entries[0]!=1) throw new IllegalStateException("Unexpected baseline transformation method");
        Files.write(Path.of(args[1]),writer.toByteArray());
        System.out.println("PASS: preserved baseline FormHandler; patched timing returns and successful-entry history hook only");
    }
}
