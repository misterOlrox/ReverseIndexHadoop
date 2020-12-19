import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.shaded.org.apache.commons.io.FileUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ReverseIndex extends Configured implements Tool {

    public final static String SEPARATOR = "\t";
    public final static String DELIMITERS = " /.,‚!`?:;‘’“”*_+—…')(\n!=\"\t#$%>@&-0123456789<{}[]";

    public static class Map
            extends Mapper<LongWritable, Text, Text, Text> {

        private final static LongWritable ZERO = new LongWritable(0);
        private final ConcurrentMap<String, Text> articleNameMap = new ConcurrentHashMap<>();

        @Override
        public void map(
                LongWritable key,
                Text value,
                Context context
        ) throws IOException, InterruptedException {
            FileSplit fileSplit = (FileSplit) context.getInputSplit();
            String filename = fileSplit.getPath().getName();
            if (key.equals(ZERO)) {
                Text articleName = new Text(value);
                articleNameMap.put(filename, articleName);
            }

            Text articleName = articleNameMap.get(filename);
            if (articleName == null) {
                throw new NullPointerException("No article name!!!");
            }
            String line = value.toString();
            StringTokenizer tokenizer = new StringTokenizer(line, DELIMITERS);
            while (tokenizer.hasMoreTokens()) {
                context.write(new Text(tokenizer.nextToken()), articleName);
            }
        }
    }

    public static class Reduce
            extends Reducer<Text, Text, Text, Text> {
        @Override
        public void reduce(
                Text key,
                Iterable<Text> values,
                Context context
        ) throws IOException, InterruptedException {
            Set<String> texts = new TreeSet<>();
            for (Text val : values) {
                texts.add(val.toString());
            }
            context.write(key, new Text(String.join(SEPARATOR, texts)));
        }
    }

    public int run(String[] args) throws Exception {
        Job job = new Job(getConf());
        job.setJarByClass(ReverseIndex.class);
        job.setJobName("wordcount");
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setMapperClass(Map.class);
        job.setCombinerClass(Reduce.class);
        job.setReducerClass(Reduce.class);
        job.setInputFormatClass(TextInputFormat.class);
        job.setOutputFormatClass(TextOutputFormat.class);
        FileInputFormat.setInputPaths(job, new Path("input"));
        FileOutputFormat.setOutputPath(job, new Path("output"));
        FileUtils.deleteDirectory(new File("output"));
        boolean success = job.waitForCompletion(true);
        return success ? 0 : 1;
    }

    public static void main(String[] args) throws Exception {
        int ret = ToolRunner.run(new ReverseIndex(), args);
        System.exit(ret);
    }
}
