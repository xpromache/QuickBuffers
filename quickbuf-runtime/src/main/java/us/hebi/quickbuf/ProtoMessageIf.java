/*-
 * #%L
 * quickbuf-runtime
 * %%
 * Copyright (C) 2019 HEBI Robotics
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package us.hebi.quickbuf;

import java.io.IOException;
import java.util.List;

/**
 * Abstract interface implemented by Protocol Message objects.
 * <p>
 * API partially copied from Google's MessageNano
 *
 * @author Florian Enner
 */
public interface  ProtoMessageIf<MessageType extends ProtoMessageIf<?>> {

    
    /**
     * Copies all fields and data from another message of the same
     * type into this message.
     *
     * @param other message with the contents to be copied
     * @return this
     */
    public abstract MessageType copyFrom(MessageType other);

    /**
     * Sets all fields and data to their default values. Does not
     * get rid of memory that was allocated.
     *
     * @return this
     */
    public abstract MessageType clear();

    /**
     * Clears all has state so that the message would serialize empty,
     * but does not set field default values and does not get rid of
     * memory that was allocated for repeated types.
     * <p>
     * Use this if you use this message for serialization purposes or
     * if you do not require default values for unset fields.
     *
     * @return this
     */
    public default MessageType clearQuick() {
        return clear();
    }

    /**
     * @return true if none of the fields in this message are set
     */
    public default boolean isEmpty() {
        throw new RuntimeException("Generated message does not implement isEmpty");
    }

    /**
     * @return true if all required fields in this message and in nested messages are set
     */
    public default boolean isInitialized() {
        return true;
    }

    /**
     * Helper method to check if this message is initialized, i.e.,
     * if all required fields are set.
     * <p>
     * Message content is not automatically checked after merging
     * new data. This method should be called manually as needed.
     *
     * @return this
     * @throws InvalidProtocolBufferException if it is not initialized.
     */
    public MessageType checkInitialized() throws InvalidProtocolBufferException ;

    /**
     * Get the number of bytes required to encode this message.
     * Returns the cached size or calls getSerializedSize which
     * sets the cached size. This is used internally when serializing
     * so the size is only computed once. If a member is modified
     * then this could be stale call getSerializedSize if in doubt.
     *
     * @return the cached size of the serialized proto form
     */
    public abstract int getCachedSize();

    /**
     * Computes the number of bytes required to encode this message.
     * The size is cached and the cached result can be retrieved
     * using getCachedSize().
     *
     * @return the size of the serialized proto form
     */
    public abstract int getSerializedSize() ;

    /**
     * Serializes the message and writes it to {@code output}.
     *
     * @param output the output to receive the serialized form.
     * @throws IOException if an error occurred writing to {@code output}.
     */
    public abstract void writeTo(ProtoSink output) throws IOException;

    /**
     * Serializes the message and writes it to the {@code output} in
     * length delimited form.
     *
     * @return this
     */
    public MessageType writeDelimitedTo(ProtoSink output) throws IOException;

    /**
     * Merges the contents for one message written in length delimited form.
     *
     * @return this
     */
    public MessageType mergeDelimitedFrom(ProtoSource input) throws IOException ;

    /**
     * Parse {@code input} as a message of this type and merge it with the
     * message being built.
     *
     * @return this
     */
    public abstract MessageType mergeFrom(ProtoSource input) throws IOException;

    /**
     * Merge {@code other} into the message being built. {@code other} must have the exact same type
     * as {@code this}.
     *
     * <p>Merging occurs as follows. For each field:<br>
     * * For singular primitive fields, if the field is set in {@code other}, then {@code other}'s
     * value overwrites the value in this message.<br>
     * * For singular message fields, if the field is set in {@code other}, it is merged into the
     * corresponding sub-message of this message using the same merging rules.<br>
     * * For repeated fields, the elements in {@code other} are concatenated with the elements in
     * this message.<br>
     * * For oneof groups, if the other message has one of the fields set, the group of this message
     * is cleared and replaced by the field of the other message, so that the oneof constraint is
     * preserved.
     *
     * <p>This is equivalent to the {@code Message::MergeFrom} method in C++.
     *
     * @return this
     */
    public default MessageType mergeFrom(MessageType other) {
        throw new RuntimeException("Generated message does not implement mergeFrom");
    }

    /**
     * Serializes this message in a JSON format compatible with
     * https://developers.google.com/protocol-buffers/docs/proto3#json.
     * <p>
     * The implementation may not fail on missing required fields, so
     * required fields would need to be checked using {@link ProtoMessage#isInitialized()}.
     *
     * @param output json sink
     */
    public default void writeTo(final JsonSink output) throws IOException {
        throw new IllegalStateException("Generated message does not implement JSON output");
    }

    /**
     * Parse {@code input} as a message of this type and merge it with the
     * message being built.
     *
     * @return this
     */
    public default MessageType mergeFrom(final JsonSource input) throws IOException {
        throw new IllegalStateException("Generated message does not implement JSON input");
    }

    /**
     * Serialize to a byte array.
     *
     * @return byte array with the serialized data.
     */
    public  byte[] toByteArray() ;
    /**
     * Indicates whether another object is "equal to" this one.
     * <p>
     * An object is considered equal when it is of the same message
     * type, contains the same fields (same has state), and all the
     * field contents are equal.
     * <p>
     * This comparison ignores unknown fields, so the serialized binary
     * form may not be equal.
     *
     * @param obj the reference object with which to compare
     * @return {@code true} if this object is the same as the obj
     * argument; {@code false} otherwise.
     */
    @Override
    public abstract boolean equals(Object obj);


    /**
     * Creates a new instance of this message with the same content
     */
    public abstract MessageType clone();

    /**
     * @return the full path to all missing required fields in the message
     */
    public List<String> getMissingFields() ;
    /**
     * @return binary representation of all fields with tags that could not be parsed
     */
    public default RepeatedByte getUnknownBytes() {
        throw new IllegalStateException("The message was generated without support for unknown bytes.");
    }

}
