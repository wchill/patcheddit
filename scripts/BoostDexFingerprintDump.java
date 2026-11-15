/*
 * Copyright 2026 wchill.
 *
 * See the included NOTICE file for GPLv3 section 7 terms.
 */

import com.android.tools.smali.dexlib2.DexFileFactory;
import com.android.tools.smali.dexlib2.iface.instruction.DualReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.SwitchPayload;
import com.android.tools.smali.dexlib2.iface.instruction.ThreeRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload;
import com.android.tools.smali.dexlib2.iface.reference.FieldReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodProtoReference;
import com.android.tools.smali.dexlib2.iface.reference.MethodReference;
import com.android.tools.smali.dexlib2.iface.reference.Reference;
import com.android.tools.smali.dexlib2.iface.reference.StringReference;
import com.android.tools.smali.dexlib2.iface.reference.TypeReference;
import com.google.gson.Gson;

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

public final class BoostDexFingerprintDump {
    private static String methodId(MethodReference method) {
        return method.getDefiningClass() + "->" + method.getName() + "("
                + String.join("", method.getParameterTypes()) + ")" + method.getReturnType();
    }

    private static String fieldId(FieldReference field) {
        return field.getDefiningClass() + "->" + field.getName() + ":" + field.getType();
    }

    private static Object reference(Reference reference) {
        if (reference instanceof MethodReference method) return List.of("method", methodId(method));
        if (reference instanceof FieldReference field) return List.of("field", fieldId(field));
        if (reference instanceof TypeReference type) return List.of("type", type.getType());
        if (reference instanceof StringReference string) return List.of("string", string.getString());
        if (reference instanceof MethodProtoReference prototype) {
            return List.of("proto", "(" + String.join("", prototype.getParameterTypes())
                    + ")" + prototype.getReturnType());
        }
        throw new IllegalArgumentException("Unsupported DEX reference: " + reference.getClass().getName());
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("Expected APK, output, and metadata paths");
        }
        var container = DexFileFactory.loadDexContainer(new File(args[0]), null);
        var gson = new Gson();
        int count = 0;
        try (var writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(Path.of(args[1]))), StandardCharsets.UTF_8))) {
            writer.write(Files.readString(Path.of(args[2]), StandardCharsets.UTF_8).strip());
            writer.newLine();
            for (String entry : container.getDexEntryNames()) {
                for (var cls : container.getEntry(entry).getDexFile().getClasses()) {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put("type", cls.getType());
                    record.put("source", cls.getSourceFile());
                    record.put("flags", cls.getAccessFlags());
                    record.put("parent", cls.getSuperclass());
                    record.put("interfaces", cls.getInterfaces());
                    var fields = new ArrayList<Object>();
                    for (var field : cls.getFields()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", fieldId(field));
                        item.put("name", field.getName());
                        item.put("type", field.getType());
                        item.put("flags", field.getAccessFlags());
                        if (field.getInitialValue() != null) {
                            item.put("initial", field.getInitialValue().toString());
                        }
                        fields.add(item);
                    }
                    record.put("fields", fields);
                    var methods = new ArrayList<Object>();
                    for (var method : cls.getMethods()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", methodId(method));
                        item.put("name", method.getName());
                        item.put("parameters", method.getParameterTypes());
                        item.put("returns", method.getReturnType());
                        item.put("flags", method.getAccessFlags());
                        var implementation = method.getImplementation();
                        if (implementation != null) {
                            item.put("registers", implementation.getRegisterCount());
                            var code = new ArrayList<Object>();
                            var offsets = new ArrayList<Integer>();
                            int address = 0;
                            for (var instruction : implementation.getInstructions()) {
                                offsets.add(address);
                                address += instruction.getCodeUnits();
                                Map<String, Object> insn = new LinkedHashMap<>();
                                insn.put("op", instruction.getOpcode().name());
                                if (instruction instanceof RegisterRangeInstruction registers) {
                                    insn.put("range", List.of(registers.getStartRegister(), registers.getRegisterCount()));
                                } else if (instruction instanceof FiveRegisterInstruction registers) {
                                    insn.put("regs", Arrays.asList(
                                            registers.getRegisterC(), registers.getRegisterD(), registers.getRegisterE(),
                                            registers.getRegisterF(), registers.getRegisterG()
                                    ).subList(0, registers.getRegisterCount()));
                                } else if (instruction instanceof ThreeRegisterInstruction registers) {
                                    insn.put("regs", List.of(registers.getRegisterA(), registers.getRegisterB(), registers.getRegisterC()));
                                } else if (instruction instanceof TwoRegisterInstruction registers) {
                                    insn.put("regs", List.of(registers.getRegisterA(), registers.getRegisterB()));
                                } else if (instruction instanceof OneRegisterInstruction registers) {
                                    insn.put("regs", List.of(registers.getRegisterA()));
                                }
                                if (instruction instanceof WideLiteralInstruction literal) {
                                    insn.put("literal", literal.getWideLiteral());
                                }
                                if (instruction instanceof OffsetInstruction offset) {
                                    insn.put("offset", offset.getCodeOffset());
                                }
                                if (instruction instanceof ReferenceInstruction ref) {
                                    insn.put("ref", reference(ref.getReference()));
                                }
                                if (instruction instanceof DualReferenceInstruction ref) {
                                    insn.put("ref2", reference(ref.getReference2()));
                                }
                                if (instruction instanceof SwitchPayload payload) {
                                    insn.put("switch", payload.getSwitchElements().stream()
                                            .map(element -> List.of(element.getKey(), element.getOffset())).toList());
                                }
                                if (instruction instanceof ArrayPayload payload) {
                                    insn.put("array", payload.getArrayElements());
                                    insn.put("width", payload.getElementWidth());
                                }
                                code.add(insn);
                            }
                            offsets.add(address);
                            item.put("code", code);
                            item.put("offsets", offsets);
                            var tries = new ArrayList<Object>();
                            for (var block : implementation.getTryBlocks()) {
                                var handlers = new ArrayList<Object>();
                                for (var handler : block.getExceptionHandlers()) {
                                    Map<String, Object> value = new LinkedHashMap<>();
                                    value.put("type", handler.getExceptionType());
                                    value.put("address", handler.getHandlerCodeAddress());
                                    handlers.add(value);
                                }
                                tries.add(List.of(block.getStartCodeAddress(),
                                        block.getStartCodeAddress() + block.getCodeUnitCount(), handlers));
                            }
                            item.put("tries", tries);
                        }
                        methods.add(item);
                    }
                    record.put("methods", methods);
                    writer.write(gson.toJson(record));
                    writer.newLine();
                    count++;
                }
            }
        }
        if (count == 0) throw new IllegalArgumentException("APK contains no DEX classes");
        System.out.println("Exported " + count + " DEX classes");
    }
}
