package wikiedits;

import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.connectors.wikiedits.WikipediaEditEvent;
import org.apache.flink.streaming.connectors.wikiedits.WikipediaEditsSource;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer011;


public class WikipediaAnalysis {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment see = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStream<WikipediaEditEvent> edits = see.addSource(new WikipediaEditsSource());
        KeyedStream<WikipediaEditEvent, String> keyedEdits = edits.keyBy(WikipediaEditEvent::getUser);

        DataStream<Tuple2<String, Long>> result = keyedEdits
                .timeWindow(Time.seconds(5))
                .aggregate(getFunction());

        result.map(Tuple2::toString)
                .addSink(new FlinkKafkaProducer011<>("localhost:9092", "wiki-results-ok", new SimpleStringSchema()));

        see.execute();
    }

    private static AggregateFunction<WikipediaEditEvent, Tuple2<String, Long>, Tuple2<String, Long>> getFunction() {
        return new AggregateFunction<WikipediaEditEvent, Tuple2<String, Long>, Tuple2<String, Long>>() {
            @Override
            public Tuple2<String, Long> createAccumulator() {
                return new Tuple2<>("", 0L);
            }

            @Override
            public Tuple2<String, Long> add(WikipediaEditEvent value, Tuple2<String, Long> accumulator) {
                accumulator.f0 = value.getUser();
                accumulator.f1 += value.getByteDiff();
                return accumulator;
            }

            @Override
            public Tuple2<String, Long> getResult(Tuple2<String, Long> accumulator) {
                return accumulator;
            }

            @Override
            public Tuple2<String, Long> merge(Tuple2<String, Long> a, Tuple2<String, Long> b) {
                return new Tuple2<>(a.f0, a.f1 + b.f1);
            }
        };
    }
}
