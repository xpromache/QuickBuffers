/*-
 * #%L
 * benchmarks
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

package us.hebi.quickbuf.benchmarks.encoding;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * This benchmark measures the time it takes to compute the size of a varint and to write it into an
 * array. It seems that while the bit instructions are constant, they are slower than the existing
 * variable implementation. With large or negative numbers they are roughly the same (on a CPU that
 * supports the instructions), but for the likely case (low positive integers) they are quite a bit
 * slower.
 *
 * (best case w/ positive integers below 128)
 * Benchmark                                    Mode  Cnt  Score   Error  Units
 * VarintBenchmark.computeVarint32Nano          avgt   10  0.143 ± 0.005  us/op
 * VarintBenchmark.computeVarint32LeadingZeros  avgt   10  0.551 ± 0.012  us/op
 * VarintBenchmark.computeVarint32Bits          avgt   10  1.404 ± 0.151  us/op
 * VarintBenchmark.writeVarint32Nano            avgt   10  0.267 ± 0.005  us/op
 * VarintBenchmark.writeVarint32Loop            avgt   10  1.478 ± 0.048  us/op
 * VarintBenchmark.writeVarint32Switch          avgt   10  1.060 ± 0.030  us/op
 *
 * (random input w/ 50% negative numbers)
 * Benchmark                                    Mode  Cnt  Score   Error  Units
 * VarintBenchmark.computeVarint32Nano          avgt   10  0.529 ± 0.066  us/op
 * VarintBenchmark.computeVarint32LeadingZeros  avgt   10  0.543 ± 0.010  us/op
 * VarintBenchmark.computeVarint32Bits          avgt   10  1.546 ± 0.050  us/op
 * VarintBenchmark.writeVarint32Nano            avgt   10  2.695 ± 0.080  us/op
 * VarintBenchmark.writeVarint32Loop            avgt   10  3.412 ± 0.096  us/op
 * VarintBenchmark.writeVarint32Switch          avgt   10  2.557 ± 0.153  us/op
 *
 * === all 1 byte
 * Benchmark                                      Mode  Cnt  Score   Error  Units
 * Varint32Benchmark.computeVarint32Bits          avgt   10  0,680 ± 0,182  us/op
 * Varint32Benchmark.computeVarint32LeadingZeros  avgt   10  0,454 ± 0,006  us/op
 * Varint32Benchmark.computeVarint32Nano          avgt   10  0,111 ± 0,001  us/op
 * Varint32Benchmark.computeVarint32_zlc_lookup   avgt   10  0,189 ± 0,001  us/op
 *
 * == production distribution
 * Benchmark                                      Mode  Cnt  Score   Error  Units
 * Varint32Benchmark.computeVarint32Bits          avgt   10  0,253 ± 0,003  us/op
 * Varint32Benchmark.computeVarint32LeadingZeros  avgt   10  0,447 ± 0,002  us/op
 * Varint32Benchmark.computeVarint32Nano          avgt   10  0,168 ± 0,019  us/op
 * Varint32Benchmark.computeVarint32_zlc_lookup   avgt   10  0,191 ± 0,004  us/op
 *
 * @author Florian Enner
 * @since 12 Sep 2014
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class Varint32Benchmark {

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(".*" + Varint32Benchmark.class.getSimpleName() + ".*")
                .verbosity(VerboseMode.NORMAL)
                .build();
        new Runner(options).run();
    }

    int[] values = new int[512];
    byte[] output = new byte[10];

    @Setup(Level.Iteration)
    public void setup() {
        Random random = new Random(System.nanoTime());
        for (int i = 0; i < values.length; i++) {
//            values[i] = Math.abs(random.nextInt()) % 128; // all 1 byte varint (best case)
//            values[i] = random.nextInt(); // 50% negative (worst case)
//            values[i] = random.nextDouble() < 0.50 ? Math.abs(random.nextInt()) : random.nextInt(); // 25% negative
//            values[i] = 1 << random.nextInt(10); // random long bit distribution
            values[i] = withProductionDistribution(random);
        }
    }

    private int withProductionDistribution(Random random) {
        float rnd = random.nextFloat() * 100f;
        if(rnd < 94.8) return 1; // 1 byte
        /*if(rnd<100)*/return 1 << 7; // 2 bytes
    }

    public static int writeRawVarint32Nano(int value, byte[] output) {
        int pos = 0;
        while (true) {
            if ((value & ~0x7F) == 0) {
                output[pos++] = (byte) (value & 0x7F);
                return value;
            } else {
                output[pos++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            }
        }
    }

    public static int computeRawVarint32SizeNano(final int value) {
        if ((value & (0xffffffff << 7)) == 0) return 1;
        if ((value & (0xffffffff << 14)) == 0) return 2;
        if ((value & (0xffffffff << 21)) == 0) return 3;
        if ((value & (0xffffffff << 28)) == 0) return 4;
        return 5;
    }

    public static int writeRawVarint32Loop(int value, byte[] output) {
        final int highestIndex = computeRawVarint32SizeBits(value) - 1;
        for (int i = 0; i < highestIndex; i++) {
            output[i] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        output[highestIndex] = (byte) (value & 0x7F);
        return value;
    }

    public static int writeRawVarint32Switch(int value, byte[] output) {
        final int numBytes = computeRawVarint32SizeLeadingZeros(value);
        int i = 0;
        switch (numBytes) {
            case 5:
                output[i++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            case 4:
                output[i++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            case 3:
                output[i++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            case 2:
                output[i++] = (byte) ((value & 0x7F) | 0x80);
                value >>>= 7;
            case 1:
                output[i] = (byte) (value & 0x7F);
        }
        return value;
    }

    public static int computeRawVarint32SizeBits(final int value) {
        final int highestBit = Integer.highestOneBit(value);
        switch (highestBit) {
            case 0:
            case 1:
            case 1 << 1:
            case 1 << 2:
            case 1 << 3:
            case 1 << 4:
            case 1 << 5:
            case 1 << 6:
                return 1;
            case 1 << 7:
            case 1 << 8:
            case 1 << 9:
            case 1 << 10:
            case 1 << 11:
            case 1 << 12:
            case 1 << 13:
                return 2;
            case 1 << 14:
            case 1 << 15:
            case 1 << 16:
            case 1 << 17:
            case 1 << 18:
            case 1 << 19:
            case 1 << 20:
                return 3;
            case 1 << 21:
            case 1 << 22:
            case 1 << 23:
            case 1 << 24:
            case 1 << 25:
            case 1 << 26:
            case 1 << 27:
            case 1 << 28:
                return 4;
            case 1 << 29:
            case 1 << 30:
            case 1 << 31:
                return 5;
        }
        throw new AssertionError();
    }

    public static int computeRawVarint32SizeLeadingZeros(final int value) {
        return ((32 - Integer.numberOfLeadingZeros(value)) + 6) / 7;
    }

    static class Varint32 {
        static int sizeOf(int value) {
            return SIZE[Integer.numberOfLeadingZeros(value)];
        }
        static final int[] SIZE = new int[33];
        static {
            for (int i = 0; i <= 32; i++) {
                SIZE[i] = 1 + (31 - i) / 7;
            }
        }
    }

    @Benchmark
    public int computeVarint32Nano() {
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            size += computeRawVarint32SizeNano(values[i]);
        }
        return size;
    }

    @Benchmark
    public int computeVarint32LeadingZeros() {
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            size += computeRawVarint32SizeLeadingZeros(values[i]);
        }
        return size;
    }

    @Benchmark
    public int computeVarint32_zlc_lookup() {
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            size += Varint32.sizeOf(values[i]);
        }
        return size;
    }

    @Benchmark
    public int computeVarint32Bits() {
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            size += computeRawVarint32SizeBits(values[i]);
        }
        return size;
    }

    @Benchmark
    public int writeVarint32Nano() {
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            size += writeRawVarint32Nano(values[i], output);
        }
        return size;
    }

    @Benchmark
    public int writeVarint32Loop() {
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            size += writeRawVarint32Loop(values[i], output);
        }
        return size;
    }

    @Benchmark
    public int writeVarint32Switch() {
        int size = 0;
        for (int i = 0; i < values.length; i++) {
            size += writeRawVarint32Switch(values[i], output);
        }
        return size;
    }
}
