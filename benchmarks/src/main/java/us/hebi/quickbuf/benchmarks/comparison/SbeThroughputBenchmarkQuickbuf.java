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

package us.hebi.quickbuf.benchmarks.comparison;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.VerboseMode;
import protos.benchmarks.real_logic.quickbuf.Examples;
import protos.benchmarks.real_logic.quickbuf.Examples.Car;
import protos.benchmarks.real_logic.quickbuf.Fix;
import protos.benchmarks.real_logic.quickbuf.Fix.MarketDataIncrementalRefreshTrades;
import us.hebi.quickbuf.JsonSink;
import us.hebi.quickbuf.ProtoSink;
import us.hebi.quickbuf.ProtoSource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * === Quickbuf RC1 (JDK 17)
 * Benchmark                                             Mode  Cnt      Score     Error   Units
 * SbeThroughputBenchmarkQuickbuf.testCarDecode         thrpt   40   3362,213 ±  67,658  ops/ms
 * SbeThroughputBenchmarkQuickbuf.testCarEncode         thrpt   40   3404,555 ±  31,683  ops/ms
 * SbeThroughputBenchmarkQuickbuf.testCarEncodeFast     thrpt   40   3689,193 ±  68,648  ops/ms
 * SbeThroughputBenchmarkQuickbuf.testMarketDecode      thrpt   40   9201,885 ±  46,797  ops/ms
 * SbeThroughputBenchmarkQuickbuf.testMarketEncode      thrpt   40  12478,487 ± 120,646  ops/ms
 * SbeThroughputBenchmarkQuickbuf.testMarketEncodeFast  thrpt   40  14332,532 ±  58,521  ops/ms
 *
 * @author Florian Enner
 * @since 16 Oct 2019
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(2)
@Warmup(iterations = 5, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 250, timeUnit = TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class SbeThroughputBenchmarkQuickbuf {

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(".*" + SbeThroughputBenchmarkQuickbuf.class.getSimpleName() + ".*(Encode|Decode).*")
                .verbosity(VerboseMode.NORMAL)
                .build();
        new Runner(options).run();
    }

    final MarketDataIncrementalRefreshTrades marketData = MarketDataIncrementalRefreshTrades.newInstance();
    final byte[] marketDecodeBuffer = buildMarketData(marketData).toByteArray();
    final Car car = Car.newInstance();
    final byte[] carDecodeBuffer = buildCarData(car).toByteArray();

    final byte[] encodeBuffer = new byte[Math.max(marketDecodeBuffer.length, carDecodeBuffer.length)];
    final ProtoSource source = ProtoSource.newArraySource();
    final ProtoSink sink = ProtoSink.newArraySink();

    final MarketDataIncrementalRefreshTrades marketDataFast = buildMarketData(MarketDataIncrementalRefreshTrades.newInstance());
    final Car carFast = buildCarData(Car.newInstance());
    final JsonSink jsonSink = JsonSink.newInstance().setPrettyPrinting(false);

    {
        // pre-compute size so we can copy the cached size in copyFrom
        marketDataFast.getSerializedSize();
        carFast.getSerializedSize();
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    public int testMarketComputeSizeOnly() throws IOException {
        return marketDataFast.getSerializedSize();
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    public int testCarComputeSizeOnly() throws IOException {
        return carFast.getSerializedSize();
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    public int testMarketWriteOnly() throws IOException {
        sink.setOutput(encodeBuffer);
        marketDataFast.writeTo(sink);
        return sink.getTotalBytesWritten();
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    public Object testMarketBuildOnly() throws IOException {
        return buildMarketData(marketDataFast);
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    public Object testCarBuildOnly() throws IOException {
        return buildCarData(carFast);
    }

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Benchmark
    public int testCarWriteOnly() throws IOException {
        sink.setOutput(encodeBuffer);
        carFast.writeTo(sink);
        return sink.getTotalBytesWritten();
    }

    @Benchmark
    public int testMarketEncodeFast() throws IOException { // no size computation
        sink.setOutput(encodeBuffer);
        marketData.copyFrom(marketDataFast).writeTo(sink);
        return sink.getTotalBytesWritten();
    }

    @Benchmark
    public int testCarEncodeFast() throws IOException { // no size computation
        sink.setOutput(encodeBuffer);
        car.copyFrom(carFast).writeTo(sink);
        return sink.getTotalBytesWritten();
    }

    @Benchmark
    public int testMarketEncode() throws IOException {
        sink.setOutput(encodeBuffer);
        buildMarketData(marketData).writeTo(sink);
        return sink.getTotalBytesWritten();
    }

    @Benchmark
    public Object testMarketDecode() throws IOException {
        source.setInput(marketDecodeBuffer);
        return marketData.clearQuick().mergeFrom(source);
    }

    @Benchmark
    public int testCarEncode() throws IOException {
        sink.setOutput(encodeBuffer);
        buildCarData(car).writeTo(sink);
        return sink.getTotalBytesWritten();
    }

    @Benchmark
    public Object testCarDecode() throws IOException {
        source.setInput(carDecodeBuffer);
        return car.clearQuick().mergeFrom(source);
    }

    @Benchmark
    public Object testMarketEncodeJson() throws IOException {
        return jsonSink.clear().writeMessage(buildMarketData(marketData)).getBytes();
    }

    @Benchmark
    public Object testCarEncodeJson() throws IOException {
        return jsonSink.clear().writeMessage(buildCarData(car)).getBytes();
    }

    public static MarketDataIncrementalRefreshTrades buildMarketData(MarketDataIncrementalRefreshTrades marketData) {
        marketData.clearQuick()
                .setTransactTime(1234L)
                .setEventTimeDelta(987)
                .setMatchEventIndicatorValue(MarketDataIncrementalRefreshTrades.MatchEventIndicator.END_EVENT_VALUE);

        for (int i = 0; i < 2; i++) {

            Fix.MdIncGrp group = marketData.getMutableMdIncGroup().next()
                    .setTradeId(1234L)
                    .setSecurityId(56789L)
                    .setNumberOfOrders(1)
                    .setRepSeq(1)
                    .setMdUpdateActionValue(Fix.MdIncGrp.MdUpdateAction.NEW_VALUE)
                    .setAggressorSideValue(Fix.MdIncGrp.Side.BUY_VALUE)
                    .setMdEntryTypeValue(Fix.MdIncGrp.MdEntryType.BID_VALUE);
            group.getMutableMdEntryPx().setMantissa(50);
            group.getMutableMdEntrySize().setMantissa(10);

        }

        return marketData;
    }

    public static Car buildCarData(Car car) {
        car.clearQuick()
                .setSerialNumber(12345)
                .setModelYear(2005)
                .setCodeValue(Car.Model.A_VALUE)
                .setAvailable(true);

        car.getMutableEngine()
                .setCapacity(4200)
                .setNumCylinders(8)
                .setManufacturerCode(ENG_MAN_CODE);

        car
                .setVehicleCode(VEHICLE_CODE)
                .setMake(MAKE)
                .setModel(MODEL);

        for (int i = 0; i < 5; i++) {
            car.addSomeNumbers(i);
        }

        car.getMutableOptionalExtras()
                .addValue(Car.Extras.SPORTS_PACK_VALUE)
                .addValue(Car.Extras.SUN_ROOF_VALUE);

        car.getMutableFuelFigures().next().setSpeed(30).setMpg(35.9F);
        car.getMutableFuelFigures().next().setSpeed(30).setMpg(49.0F);
        car.getMutableFuelFigures().next().setSpeed(30).setMpg(40.0F);

        Examples.PerformanceFigures perfFigures = car.getMutablePerformance().next().setOctaneRating(95);
        perfFigures.getMutableAcceleration().next().setMph(30).setSeconds(4.0f);
        perfFigures.getMutableAcceleration().next().setMph(60).setSeconds(7.5f);
        perfFigures.getMutableAcceleration().next().setMph(100).setSeconds(12.2f);

        perfFigures = car.getMutablePerformance().next().setOctaneRating(99);
        perfFigures.getMutableAcceleration().next().setMph(30).setSeconds(3.8f);
        perfFigures.getMutableAcceleration().next().setMph(60).setSeconds(7.1f);
        perfFigures.getMutableAcceleration().next().setMph(100).setSeconds(11.8f);

        return car;
    }

    static final String VEHICLE_CODE = "abcdef";
    static final String ENG_MAN_CODE = "abc";
    static final String MAKE = "AUDI";
    static final String MODEL = "R8";

}
