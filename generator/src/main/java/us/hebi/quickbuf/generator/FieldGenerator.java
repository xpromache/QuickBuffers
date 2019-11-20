/*-
 * #%L
 * quickbuf-generator
 * %%
 * Copyright (C) 2019 HEBI Robotics
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package us.hebi.quickbuf.generator;

import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.HashMap;

/**
 * This class generates all serialization logic and field related accessors.
 * It is a bit of a mess due to lots of switch statements, but I found that
 * splitting the types up similarly to how the protobuf-generator code is
 * organized makes it really difficult to find and manage duplicate code,
 * and to keep track of where things are being called.
 *
 * @author Florian Enner
 * @since 07 Aug 2019
 */
public class FieldGenerator {

    protected RequestInfo.FieldInfo getInfo() {
        return info;
    }

    protected void generateMemberFields(TypeSpec.Builder type) {
        FieldSpec.Builder field = FieldSpec.builder(storeType, info.getFieldName())
                .addJavadoc(named("$commentLine:L"))
                .addModifiers(Modifier.PRIVATE);

        if (info.isRepeated() && info.isMessageOrGroup()) {
            field.addModifiers(Modifier.FINAL).initializer("$T.newEmptyInstance($T.getFactory())", RuntimeClasses.RepeatedMessage, info.getTypeName());
        } else if (info.isRepeated() && info.isEnum()) {
            field.addModifiers(Modifier.FINAL).initializer("$T.newEmptyInstance($T.converter())", RuntimeClasses.RepeatedEnum, info.getTypeName());
        } else if (info.isRepeated() || info.isBytes()) {
            field.addModifiers(Modifier.FINAL).initializer("$T.newEmptyInstance()", storeType);
        } else if (info.isMessageOrGroup()) {
            field.addModifiers(Modifier.FINAL).initializer("$T.newInstance()", storeType);
        } else if (info.isString()) {
            field.addModifiers(Modifier.FINAL).initializer("new $T(0)", storeType);
        } else if (info.isPrimitive() || info.isEnum()) {
            // no initializer needed
        } else {
            throw new IllegalStateException("unhandled field: " + info.getDescriptor());
        }
        type.addField(field.build());

        if (info.isBytes() && info.hasDefaultValue()) {
            // byte[] default values are stored as utf8 strings, so we need to convert it first
            type.addField(FieldSpec.builder(ArrayTypeName.get(byte[].class), info.getDefaultFieldName())
                    .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(named("$abstractMessage:T.bytesDefaultValue(\"$default:L\")"))
                    .build());
        }
    }

    protected void generateClearCode(MethodSpec.Builder method) {
        if (info.isRepeated() || info.isMessageOrGroup()) {
            method.addStatement(named("$field:N.clear()"));

        } else if (info.isPrimitive() || info.isEnum()) {
            method.addStatement(named("$field:N = $default:L"));

        } else if (info.isString()) {
            method.addStatement(named("$field:N.setLength(0)"));
            if (info.hasDefaultValue()) {
                method.addStatement(named("$field:N.append($default:S)"));
            }

        } else if (info.isBytes()) {
            method.addStatement(named("$field:N.clear()"));
            if (info.hasDefaultValue())
                method.addStatement(named("$field:N.copyFrom($defaultField:N)"));

        } else {
            throw new IllegalStateException("unhandled field: " + info.getDescriptor());
        }
    }

    protected void generateClearQuickCode(MethodSpec.Builder method) {
        if (info.isMessageOrGroup()) { // includes repeated messages
            method.addStatement(named("$field:N.clearQuick()"));
        } else if (info.isRepeated() || info.isBytes()) {
            method.addStatement(named("$field:N.clear()"));
        } else if (info.isString()) {
            method.addStatement(named("$field:N.setLength(0)"));
        } else if (info.isPrimitive() || info.isEnum()) {
            // do nothing
        } else {
            throw new IllegalStateException("unhandled field: " + info.getDescriptor());
        }
    }

    protected void generateCopyFromCode(MethodSpec.Builder method) {
        if (info.isRepeated() || info.isBytes() || info.isMessageOrGroup()) {
            method.addStatement(named("$field:N.copyFrom(other.$field:N)"));

        } else if (info.isPrimitive() || info.isEnum()) {
            method.addStatement(named("$field:N = other.$field:N"));

        } else if (info.isString()) {
            method.addStatement(named("$field:N.setLength(0)"));
            method.addStatement(named("$field:N.append(other.$field:N)"));

        } else {
            throw new IllegalStateException("unhandled field: " + info.getDescriptor());
        }
    }

    protected void generateEqualsStatement(MethodSpec.Builder method) {
        if (info.isRepeated() || info.isBytes() || info.isMessageOrGroup()) {
            method.addNamedCode("$field:N.equals(other.$field:N)", m);

        } else if (info.isString()) {
            method.addNamedCode("$protoUtil:T.isEqual($field:N, other.$field:N)", m);

        } else if (typeName == TypeName.DOUBLE) {
            method.addNamedCode("Double.doubleToLongBits($field:N) == Double.doubleToLongBits(other.$field:N)", m);

        } else if (typeName == TypeName.FLOAT) {
            method.addNamedCode("Float.floatToIntBits($field:N) == Float.floatToIntBits(other.$field:N)", m);

        } else if (info.isPrimitive() || info.isEnum()) {
            method.addNamedCode("$field:N == other.$field:N", m);

        } else {
            throw new IllegalStateException("unhandled field: " + info.getDescriptor());
        }
    }

    protected void generateMergingCode(MethodSpec.Builder method) {
        if (info.isRepeated()) {
            method
                    .addCode(clearOtherOneOfs)
                    .addStatement("int nextTagPosition")
                    .addNamedCode("do {$>\n" +
                            "// look ahead for more items so we resize only once\n" +
                            "if ($field:N.remainingCapacity() == 0) {$>\n" +
                            "int count = $protoSource:T.getRepeatedFieldArrayLength(input, $tag:L);\n" +
                            "$field:N.reserve(count);\n" +
                            "$<}\n", m)
                    .addNamedCode(info.isPrimitive() || info.isEnum() ?
                            "$field:N.add(input.read$capitalizedType:L());\n"
                            : "input.read$capitalizedType:L($field:N.next()$secondArgs:L);\n", m)
                    .addNamedCode("" +
                            "nextTagPosition = input.getPosition();\n" +
                            "$<} while (input.readTag() == $tag:L);\n" +
                            "input.rewindToPosition(nextTagPosition);\n", m)
                    .addStatement(named("$setHas:L"));

        } else if (info.isString() || info.isMessageOrGroup() || info.isBytes()) {
            method
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("input.read$capitalizedType:L($field:N$secondArgs:L)"))
                    .addStatement(named("$setHas:L"));

        } else if (info.isPrimitive()) {
            method
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("$field:N = input.read$capitalizedType:L()"))
                    .addStatement(named("$setHas:L"));

        } else if (info.isEnum()) {
            method
                    .addCode(clearOtherOneOfs)
                    .addStatement("final int value = input.readInt32()")
                    .beginControlFlow("if ($T.forNumber(value) != null)", typeName)
                    .addStatement(named("$field:N = value"))
                    .addStatement(named("$setHas:L"));

            if (info.getParentTypeInfo().isStoreUnknownFields()) {
                method.nextControlFlow("else")
                        .addStatement("input.copyBytesSinceMark($N)", RuntimeClasses.unknownBytesField);
            }

            method.endControlFlow();

        } else {
            throw new IllegalStateException("unhandled field: " + info.getDescriptor());
        }
    }

    protected void generateMergingCodeFromPacked(MethodSpec.Builder method) {
        if (info.isFixedWidth()) {

            // For fixed width types we can copy the raw memory
            method.addCode(clearOtherOneOfs);
            method.addStatement(named("input.readPacked$capitalizedType:L($field:N)"));
            method.addStatement(named("$setHas:L"));

        } else if (info.isPrimitive() || info.isEnum()) {

            // We don't know how many items there actually are, so we need to
            // look-ahead once we run out of space in the backing store.
            method
                    .addCode(clearOtherOneOfs)
                    .addStatement("final int length = input.readRawVarint32()")
                    .addStatement("final int limit = input.pushLimit(length)")
                    .beginControlFlow("while (input.getBytesUntilLimit() > 0)")

                    // Defer count-checks until we run out of capacity
                    .addComment("look ahead for more items so we resize only once")
                    .beginControlFlow("if ($N.remainingCapacity() == 0)", info.getFieldName())
                    .addStatement("final int position = input.getPosition()")
                    .addStatement("int count = 0")
                    .beginControlFlow("while (input.getBytesUntilLimit() > 0)")
                    .addStatement(named("input.read$capitalizedType:L()"))
                    .addStatement("count++")
                    .endControlFlow()
                    .addStatement("input.rewindToPosition(position)")
                    .addStatement(named("$field:N.reserve(count)"))
                    .endControlFlow()

                    // Add data
                    .addStatement(named("$field:N.add(input.read$capitalizedType:L())"))
                    .endControlFlow()

                    .addStatement("input.popLimit(limit)")
                    .addStatement(named("$setHas:L"));

        } else {
            // Only primitives and enums can be packed
            throw new IllegalStateException("unhandled field: " + info.getDescriptor());
        }
    }

    protected void generateSerializationCode(MethodSpec.Builder method) {
        m.put("writeTagToOutput", generateWriteVarint32(getInfo().getTag()));
        if (info.isPacked()) {
            m.put("writePackedTagToOutput", generateWriteVarint32(getInfo().getPackedTag()));
        }

        if (info.isPacked() && info.isFixedWidth()) {
            method.addNamedCode("" +
                    "$writePackedTagToOutput:L" +
                    "output.writePacked$capitalizedType:LNoTag($field:N);\n", m);

        } else if (info.isPacked()) {
            method.addNamedCode("" +
                    "int dataSize = 0;\n" +
                    "for (int i = 0; i < $field:N.length(); i++) {$>\n" +
                    "dataSize += $protoSink:T.compute$capitalizedType:LSizeNoTag($field:N.$getRepeatedIndex_i:L);\n" +
                    "$<}\n" +
                    "$writePackedTagToOutput:L" +
                    "output.writeRawVarint32(dataSize);\n" +
                    "for (int i = 0; i < $field:N.length(); i++) {$>\n" +
                    "output.write$capitalizedType:LNoTag($field:N.$getRepeatedIndex_i:L);\n" +
                    "$<}\n", m);

        } else if (info.isRepeated()) {
            method.addNamedCode("" +
                    "for (int i = 0; i < $field:N.length(); i++) {$>\n" +
                    "$writeTagToOutput:L" +
                    "output.write$capitalizedType:LNoTag($field:N.$getRepeatedIndex_i:L);\n" +
                    "$<}\n", m);

        } else {
            // unroll varint tag loop
            method.addNamedCode("$writeTagToOutput:L", m);
            method.addStatement(named("output.write$capitalizedType:LNoTag($field:N)")); // non-repeated
        }
    }

    private static String generateWriteVarint32(int value) {
        // Split tag into individual bytes
        int[] bytes = new int[5];
        int numBytes = 0;
        while (true) {
            if ((value & ~0x7F) == 0) {
                bytes[numBytes++] = value;
                break;
            } else {
                bytes[numBytes++] = (value & 0x7F) | 0x80;
                value >>>= 7;
            }
        }

        // Write tag bytes as efficiently as possible
        String output = "";
        switch (numBytes) {

            case 4:
            case 5:
                final int fourBytes = (bytes[3] << 24 | bytes[2] << 16 | bytes[1] << 8 | bytes[0]);
                output = "output.writeRawLittleEndian32(" + fourBytes + ");\n";
                if (numBytes == 5) output += "output.writeRawByte((byte) " + bytes[4] + ");\n";
                break;

            case 2:
            case 3:
                final int twoBytes = (bytes[1] << 8 | bytes[0]);
                output = "output.writeRawLittleEndian16((short) " + twoBytes + ");\n";
                if (numBytes == 3) output += "output.writeRawByte((byte) " + bytes[2] + ");\n";
                break;

            default:
                for (int i = 0; i < numBytes; i++) {
                    output += "output.writeRawByte((byte) " + bytes[i] + ");\n";
                }

        }
        return output;
    }

    protected void generateComputeSerializedSizeCode(MethodSpec.Builder method) {
        if (info.isFixedWidth() && info.isPacked()) {
            method.addNamedCode("" +
                    "final int dataSize = $fixedWidth:L * $field:N.length();\n" +
                    "size += $bytesPerTag:L + dataSize + $protoSink:T.computeRawVarint32Size(dataSize);\n", m);

        } else if (info.isFixedWidth() && info.isRepeated()) { // non packed
            method.addStatement(named("size += ($bytesPerTag:L + $fixedWidth:L) * $field:N.length()"));

        } else if (info.isPacked()) {
            method.addNamedCode("" +
                    "int dataSize = 0;\n" +
                    "final int length = $field:N.length();\n" +
                    "for (int i = 0; i < length; i++) {$>\n" +
                    "dataSize += $protoSink:T.compute$capitalizedType:LSizeNoTag($field:N.$getRepeatedIndex_i:L);\n" +
                    "$<}\n" +
                    "size += $bytesPerTag:L + dataSize + $protoSink:T.computeRawVarint32Size(dataSize);\n", m);

        } else if (info.isRepeated()) { // non packed
            method.addNamedCode("" +
                    "int dataSize = 0;\n" +
                    "final int length = $field:N.length();\n" +
                    "for (int i = 0; i < length; i++) {$>\n" +
                    "dataSize += $protoSink:T.compute$capitalizedType:LSizeNoTag($field:N.$getRepeatedIndex_i:L);\n" +
                    "$<}\n" +
                    "size += ($bytesPerTag:L * length) + dataSize;\n", m);

        } else if (info.isFixedWidth()) {
            method.addStatement("size += $L", (info.getBytesPerTag() + info.getFixedWidth())); // non-repeated

        } else {
            method.addStatement(named("size += $bytesPerTag:L + $protoSink:T.compute$capitalizedType:LSizeNoTag($field:N)")); // non-repeated
        }

    }

    protected void generateJsonSerializationCode(MethodSpec.Builder method) {
        if (info.isEnum() && !info.isRepeated()) {
            method.addStatement(named("output.writeField($jsonKeys:T.$field:N, $field:N, $type:T.converter())"));
        } else {
            method.addStatement(named("output.writeField($jsonKeys:T.$field:N, $field:N)"));
        }
    }

    protected void generateMemberMethods(TypeSpec.Builder type) {
        generateHasMethod(type);
        generateClearMethod(type);
        generateGetMethods(type);
        if (info.isEnum()) {
            generateExtraEnumAccessors(type);
        }
        generateSetMethods(type);
    }

    protected void generateHasMethod(TypeSpec.Builder type) {
        type.addMethod(MethodSpec.methodBuilder(info.getHazzerName())
                .addAnnotations(info.getMethodAnnotations())
                .addModifiers(Modifier.PUBLIC)
                .returns(TypeName.BOOLEAN)
                .addStatement(named("return $getHas:L"))
                .build());
    }

    protected void generateSetMethods(TypeSpec.Builder type) {
        if (info.isRepeated() || info.isBytes()) {

            MethodSpec adder = MethodSpec.methodBuilder(info.getAdderName())
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(info.getInputParameterType(), "value", Modifier.FINAL)
                    .returns(info.getParentType())
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("$setHas:L"))
                    .addStatement(named("$field:N.add(value)"))
                    .addStatement(named("return this"))
                    .build();
            type.addMethod(adder);

            MethodSpec.Builder addAll = MethodSpec.methodBuilder("addAll" + info.getUpperName())
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ArrayTypeName.of(info.getInputParameterType()), "values", Modifier.FINAL)
                    .varargs(true)
                    .returns(info.getParentType())
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("$setHas:L"))
                    .addStatement(named("$field:N.addAll(values)"))
                    .addStatement(named("return this"));
            type.addMethod(addAll.build());

        } else if (info.isMessageOrGroup()) {
            MethodSpec setter = MethodSpec.methodBuilder(info.getSetterName())
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(info.getParentType())
                    .addParameter(typeName, "value", Modifier.FINAL)
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("$setHas:L"))
                    .addStatement(named("$field:N.copyFrom(value)"))
                    .addStatement(named("return this"))
                    .build();
            type.addMethod(setter);

        } else if (info.isString()) {
            MethodSpec setter = MethodSpec.methodBuilder(info.getSetterName())
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(info.getInputParameterType(), "value", Modifier.FINAL)
                    .returns(info.getParentType())
                    .addCode(clearOtherOneOfs)
                    .addNamedCode("" +
                            "$setHas:L;\n" +
                            "$field:N.setLength(0);\n" +
                            "$field:N.append(value);\n" +
                            "return this;\n", m)
                    .build();
            type.addMethod(setter);

        } else if (info.isPrimitive() || info.isEnum()) {
            MethodSpec setter = MethodSpec.methodBuilder(info.getSetterName())
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(info.getTypeName(), "value", Modifier.FINAL)
                    .returns(info.getParentType())
                    .addCode(clearOtherOneOfs)
                    .addNamedCode("" +
                            "$setHas:L;\n" +
                            "$field:N = $valueOrNumber:L;\n" +
                            "return this;\n", m)
                    .build();
            type.addMethod(setter);

        }

    }

    /**
     * Enums are odd because they need to be converter back and forth and they
     * don't have the same type as the internal/repeated store. The normal
     * accessors provide access to the enum value, but for performance reasons
     * we also add accessors for the internal storage type that do not require
     * conversions.
     *
     * @param type
     */
    protected void generateExtraEnumAccessors(TypeSpec.Builder type) {
        if (!info.isEnum())
            return;

        if (!info.isRepeated()) {

            // Overload to get the internal store without conversion
            MethodSpec.Builder getNumber = MethodSpec.methodBuilder(info.getGetterName() + "Number")
                    .addAnnotations(info.getMethodAnnotations())
                    .addJavadoc(named("" +
                            "Gets the value of the internal enum store. The result is\n" +
                            "equivalent to {@link $message:T#$getMethod:N()}.getNumber().\n"))
                    .addModifiers(Modifier.PUBLIC)
                    .returns(int.class)
                    .addCode(enforceHasCheck)
                    .addStatement(named("return $field:N"));

            // Overload to set the internal value without conversion
            MethodSpec.Builder setNumber = MethodSpec.methodBuilder(info.getSetterName() + "Number")
                    .addAnnotations(info.getMethodAnnotations())
                    .addJavadoc(named("" +
                            "Sets the value of the internal enum store. This does not\n" +
                            "do any validity checks, so be sure to use appropriate value\n" +
                            "constants from {@link $type:T}. Setting an invalid value\n" +
                            "can cause {@link $message:T#$getMethod:N()} to return null\n"))
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, "value", Modifier.FINAL)
                    .returns(info.getParentType())
                    .addNamedCode("" +
                            "$setHas:L;\n" +
                            "$field:N = value;\n" +
                            "return this;\n", m);

            type.addMethod(getNumber.build());
            type.addMethod(setNumber.build());

        } else {

            MethodSpec adder = MethodSpec.methodBuilder(info.getAdderName() + "Number")
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, "value", Modifier.FINAL)
                    .returns(info.getParentType())
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("$setHas:L"))
                    .addStatement(named("$field:N.add(value)"))
                    .addStatement(named("return this"))
                    .build();
            type.addMethod(adder);

            MethodSpec.Builder addAll = MethodSpec.methodBuilder("addAll" + info.getUpperName() + "Numbers")
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ArrayTypeName.of(int.class), "values", Modifier.FINAL)
                    .varargs(true)
                    .returns(info.getParentType())
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("$setHas:L"))
                    .addStatement(named("$field:N.addAll(values)"))
                    .addStatement(named("return this"));
            type.addMethod(addAll.build());

        }
    }

    protected void generateGetMethods(TypeSpec.Builder type) {
        MethodSpec.Builder getter = MethodSpec.methodBuilder(info.getGetterName())
                .addAnnotations(info.getMethodAnnotations())
                .addModifiers(Modifier.PUBLIC)
                .addCode(enforceHasCheck);

        if (!info.isEnum() || info.isRepeated()) {
            getter.returns(storeType).addStatement(named("return $field:N"));
        } else if (!info.hasDefaultValue()) {
            getter.returns(typeName).addStatement(named("return $type:T.forNumber($field:N)"));
        } else {
            getter.returns(typeName).addStatement(named("return $type:T.forNumberOr($field:N, $defaultEnumValue:L)"));
        }

        if (info.isRepeated() || info.isMessageOrGroup() || info.isBytes() || info.isString()) {
            getter.addJavadoc(named("" +
                    "This method returns the internal storage object without modifying any has state.\n" +
                    "The returned object should not be modified and be treated as read-only.\n" +
                    "\n" +
                    "Use {@link #$getMutableMethod:N()} if you want to modify it.\n"));

            MethodSpec mutableGetter = MethodSpec.methodBuilder(info.getMutableGetterName())
                    .addJavadoc(named("" +
                            "This method returns the internal storage object and sets the corresponding\n" +
                            "has state. The returned object will become part of this message and its\n" +
                            "contents may be modified as long as the has state is not cleared.\n"))
                    .addAnnotations(info.getMethodAnnotations())
                    .addModifiers(Modifier.PUBLIC)
                    .returns(storeType)
                    .addCode(clearOtherOneOfs)
                    .addStatement(named("$setHas:L"))
                    .addStatement(named("return $field:N"))
                    .build();

            type.addMethod(getter.build());
            type.addMethod(mutableGetter);
        } else {
            type.addMethod(getter.build());
        }

    }

    protected void generateClearMethod(TypeSpec.Builder type) {
        MethodSpec.Builder method = MethodSpec.methodBuilder(info.getClearName())
                .addAnnotations(info.getMethodAnnotations())
                .addModifiers(Modifier.PUBLIC)
                .returns(info.getParentType())
                .addStatement(named("$clearHas:L"));
        generateClearCode(method);
        method.addStatement("return this");
        type.addMethod(method.build());
    }

    private CodeBlock generateClearOtherOneOfs() {
        if (!info.hasOtherOneOfFields())
            return EMPTY_BLOCK;

        return CodeBlock.builder()
                .addStatement("$N()", info.getClearOtherOneOfName())
                .build();
    }

    private CodeBlock generateEnforceHasCheck() {
        if (!info.getParentTypeInfo().isEnforceHasChecks())
            return EMPTY_BLOCK;

        return CodeBlock.builder()
                .beginControlFlow("if (!$N())", info.getHazzerName())
                .addStatement("throw new $T($S)", IllegalStateException.class,
                        "Field is not set. Check has state before accessing.")
                .endControlFlow()
                .build();
    }

    protected FieldGenerator(RequestInfo.FieldInfo info) {
        this.info = info;
        typeName = info.getTypeName();
        storeType = info.getStoreType();
        clearOtherOneOfs = generateClearOtherOneOfs();
        enforceHasCheck = generateEnforceHasCheck();

        // Common-variable map for named arguments
        m.put("field", info.getFieldName());
        m.put("default", info.getDefaultValue());
        if (info.isEnum()) {
            m.put("default", info.hasDefaultValue() ? info.getTypeName() + "." + info.getDefaultValue() + "_VALUE" : "0");
            m.put("defaultEnumValue", info.getTypeName() + "." + info.getDefaultValue());
        }
        m.put("commentLine", info.getJavadoc());
        m.put("getMutableMethod", info.getMutableGetterName());
        m.put("getMethod", info.getGetterName());
        m.put("setMethod", info.getSetterName());
        m.put("addMethod", info.getAdderName());
        m.put("hasMethod", info.getHazzerName());
        m.put("getHas", info.getHasBit());
        m.put("setHas", info.getSetBit());
        m.put("clearHas", info.getClearBit());
        m.put("message", info.getParentType());
        m.put("type", typeName);
        m.put("number", info.getNumber());
        m.put("tag", info.getTag());
        m.put("capitalizedType", FieldUtil.getCapitalizedType(info.getDescriptor().getType()));
        m.put("secondArgs", info.isGroup() ? ", " + info.getNumber() : "");
        m.put("defaultField", info.getDefaultFieldName());
        m.put("bytesPerTag", info.getBytesPerTag());
        m.put("valueOrNumber", info.isEnum() ? "value.getNumber()" : "value");
        if (info.isPackable()) m.put("packedTag", info.getPackedTag());
        if (info.isFixedWidth()) m.put("fixedWidth", info.getFixedWidth());
        if (info.isRepeated())
            m.put("getRepeatedIndex_i", info.isPrimitive() || info.isEnum() ? "array()[i]" : "get(i)");

        // utility classes
        m.put("jsonKeys", getInfo().getParentTypeInfo().getJsonKeysClass());
        m.put("abstractMessage", RuntimeClasses.AbstractMessage);
        m.put("protoSource", RuntimeClasses.ProtoSource);
        m.put("protoSink", RuntimeClasses.ProtoSink);
        m.put("protoUtil", RuntimeClasses.ProtoUtil);
    }

    protected final RequestInfo.FieldInfo info;
    protected final TypeName typeName;
    protected final TypeName storeType;
    protected final CodeBlock clearOtherOneOfs;
    protected final CodeBlock enforceHasCheck;
    private static final CodeBlock EMPTY_BLOCK = CodeBlock.builder().build();

    protected final HashMap<String, Object> m = new HashMap<>();

    private CodeBlock named(String format, Object... args /* does nothing, but makes IDE hints disappear */) {
        return CodeBlock.builder().addNamed(format, m).build();
    }

}