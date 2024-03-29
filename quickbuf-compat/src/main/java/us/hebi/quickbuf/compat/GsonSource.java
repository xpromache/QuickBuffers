/*-
 * #%L
 * quickbuf-benchmarks
 * %%
 * Copyright (C) 2019 - 2020 HEBI Robotics
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

package us.hebi.quickbuf.compat;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import us.hebi.quickbuf.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Implementation of a JsonSource using GSON.
 * <p>
 * Warning: this implementation has not been tested extensively against bad inputs.
 *
 * @author Florian Enner
 * @since 07 Sep 2020
 */
public class GsonSource extends JsonSource {

    public GsonSource(String string) {
        this(new StringReader(string));
    }

    public GsonSource(Reader reader) {
        this(new JsonReader(reader));
        this.reader.setLenient(true); // allow nan/infinity
    }

    /**
     * @param jsonReader custom json reader. Should be lenient to allow nan/infinity
     */
    public GsonSource(JsonReader jsonReader) {
        this.reader = jsonReader;
    }

    final JsonReader reader;

    public GsonSource setIgnoreUnknownFields(final boolean ignoreUnknownFields) {
        super.setIgnoreUnknownFields(ignoreUnknownFields);
        return this;
    }

    @Override
    public double readDouble() throws IOException {
        return reader.nextDouble();
    }

    @Override
    public int readInt32() throws IOException {
        return reader.nextInt();
    }

    @Override
    public long readInt64() throws IOException {
        return reader.nextLong();
    }

    @Override
    public boolean readBool() throws IOException {
        return reader.nextBoolean();
    }

    @Override
    public <T extends ProtoEnum<?>> T readEnum(ProtoEnum.EnumConverter<T> converter) throws IOException {
        if (reader.peek() == JsonToken.NUMBER) {
            return converter.forNumber(reader.nextInt());
        } else {
            return converter.forName(reader.nextString());
        }
    }

    @Override
    public void readString(Utf8String store) throws IOException {
        store.copyFrom(reader.nextString());
    }

    @Override
    public void readBytes(RepeatedByte store) throws IOException {
        decodeBase64(reader.nextString(), store.clear());
    }

    @Override
    public ProtoSource readBytesAsSource() throws IOException {
        RepeatedByte store = RepeatedByte.newEmptyInstance();
        decodeBase64(reader.nextString(), store);
        return ProtoSource.newInstance(store);
    }

    @Override
    public void skipValue() throws IOException {
        reader.skipValue();
    }

    @Override
    public boolean beginObject() throws IOException {
        reader.beginObject();
        return true;
    }

    @Override
    public void endObject() throws IOException {
        reader.endObject();
    }

    @Override
    public void beginArray() throws IOException {
        reader.beginArray();
    }

    @Override
    public void endArray() throws IOException {
        reader.endArray();
    }

    @Override
    public boolean isAtEnd() throws IOException {
        return !reader.hasNext();
    }

    @Override
    protected boolean isAtNull() throws IOException {
        return reader.peek() == JsonToken.NULL;
    }

    @Override
    protected CharSequence readFieldName() throws IOException {
        return reader.nextName();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

}
