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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import us.hebi.quickbuf.*;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * Basic implementation of a JsonSource using GSON.
 * <p>
 * Warning: this implementation has not been tested extensively against bad inputs.
 *
 * @author Florian Enner
 * @since 26 Feb 2022
 */
public class JacksonSource extends JsonSource {

    public JacksonSource(String string) throws IOException {
        this(new StringReader(string));
    }

    public JacksonSource(Reader reader) throws IOException {
        this(Parsers.factory.createParser(reader));
    }

    private static class Parsers {
        private static final JsonFactory factory = new JsonFactory()
                .configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true); // allow nan/inf
    }

    /**
     * @param jsonReader custom json reader. Should be lenient to allow nan/infinity
     */
    public JacksonSource(JsonParser jsonReader) {
        this.reader = jsonReader;
    }

    final JsonParser reader;

    public JacksonSource setIgnoreUnknownFields(final boolean ignoreUnknownFields) {
        super.setIgnoreUnknownFields(ignoreUnknownFields);
        return this;
    }

    @Override
    public double readDouble() throws IOException {
        next();
        return reader.getValueAsDouble();
    }

    @Override
    public int readInt32() throws IOException {
        next();
        return reader.getValueAsInt();
    }

    @Override
    public long readInt64() throws IOException {
        next();
        return reader.getValueAsLong();
    }

    @Override
    public boolean readBool() throws IOException {
        next();
        return reader.getValueAsBoolean();
    }

    @Override
    public <T extends ProtoEnum<?>> T readEnum(ProtoEnum.EnumConverter<T> converter) throws IOException {
        switch (next()) {
            case VALUE_NULL:
                return null;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return converter.forNumber(reader.getIntValue());
            default:
                return converter.forName(reader.getValueAsString());
        }
    }

    @Override
    public void readString(Utf8String store) throws IOException {
        next();
        store.copyFrom(reader.getValueAsString());
    }

    @Override
    public void readBytes(RepeatedByte store) throws IOException {
        next();
        store.copyFrom(reader.getBinaryValue());
    }

    @Override
    public ProtoSource readBytesAsSource() throws IOException {
        if (next() == JsonToken.VALUE_NULL) {
            return ProtoSource.newArraySource();
        }
        return ProtoSource.newInstance(reader.getBinaryValue());
    }

    @Override
    public void skipValue() throws IOException {
        // next() gets us to the value
        switch (next()) {
            case START_OBJECT:
                skipObject();
                break;
            case START_ARRAY:
                skipArray();
                break;
            default:
                // skips the value and loads the next token
                peek();
        }
    }

    private void skipObject() throws IOException {
        int level = 1;
        while (level != 0) {
            switch (next()) {
                case START_OBJECT:
                    level++;
                    break;
                case END_OBJECT:
                    level--;
                    break;
            }
        }
    }

    private void skipArray() throws IOException {
        int level = 1;
        while (level != 0) {
            switch (next()) {
                case START_ARRAY:
                    level++;
                    break;
                case END_ARRAY:
                    level--;
                    break;
            }
        }
    }

    @Override
    public boolean beginObject() throws IOException {
        readExpectedToken(JsonToken.START_OBJECT);
        return true;
    }

    @Override
    public void endObject() throws IOException {
        readExpectedToken(JsonToken.END_OBJECT);
    }

    @Override
    public void beginArray() throws IOException {
        readExpectedToken(JsonToken.START_ARRAY);
    }

    @Override
    public void endArray() throws IOException {
        readExpectedToken(JsonToken.END_ARRAY);
    }

    @Override
    public boolean isAtEnd() throws IOException {
        JsonToken token = peek();
        return token == JsonToken.END_OBJECT ||
                token == JsonToken.END_ARRAY ||
                reader.isClosed();
    }

    @Override
    protected boolean isAtNull() throws IOException {
        return peek() == JsonToken.VALUE_NULL;
    }

    @Override
    protected CharSequence readFieldName() throws IOException {
        readExpectedToken(JsonToken.FIELD_NAME);
        return reader.getCurrentName();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    private JsonToken next() throws IOException {
        try {
            return peekToken != null ? peekToken : reader.nextToken();
        } finally {
            peekToken = null;
        }
    }

    private JsonToken peek() throws IOException {
        return peekToken != null ? peekToken : (peekToken = reader.nextToken());
    }

    private void readExpectedToken(JsonToken token) throws IOException {
        if (next() != token) {
            throw unexpectedTokenError(token);
        }
    }

    private IOException unexpectedTokenError(JsonToken expectedToken) {
        return new JsonParseException(reader, "Expected " + expectedToken.asString() + " but was "
                + reader.getCurrentToken(), reader.currentTokenLocation());
    }

    JsonToken peekToken = null;

}
