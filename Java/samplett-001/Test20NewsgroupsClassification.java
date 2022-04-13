package com.github.tteofili.looseen;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.classification.CachingNaiveBayesClassifier;
import org.apache.lucene.classification.Classifier;
import org.apache.lucene.classification.KNearestNeighborClassifier;
import org.apache.lucene.classification.SimpleNaiveBayesClassifier;
import org.apache.lucene.classification.utils.ConfusionMatrixGenerator;
import org.apache.lucene.classification.utils.DatasetSplitter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.AfterEffectL;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelG;
import org.apache.lucene.search.similarities.BasicModelP;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.DistributionLL;
import org.apache.lucene.search.similarities.DistributionSPL;
import org.apache.lucene.search.similarities.IBSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.LambdaDF;
import org.apache.lucene.search.similarities.LambdaTTF;
import org.apache.lucene.search.similarities.Normalization;
import org.apache.lucene.search.similarities.NormalizationH1;
import org.apache.lucene.search.similarities.NormalizationH3;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.LuceneTestCase;
import org.junit.Test;

@LuceneTestCase.SuppressSysoutChecks(bugUrl = "none")
public final class Test20NewsgroupsClassification extends LuceneTestCase {

    private static final String PREFIX = "/Users/teofili/data";
    private static final String INDEX = PREFIX + "/20n/index";

    private static final String CATEGORY_FIELD = "category";
    private static final String BODY_FIELD = "body";
    private static final String SUBJECT_FIELD = "subject";

    private static boolean index = false;
    private static boolean split = true;

    @Test
    public void test20Newsgroups() throws Exception {

        String indexProperty = System.getProperty("index");
        if (indexProperty != null) {
            try {
                index = Boolean.valueOf(indexProperty);
            } catch (Exception e) {
                // ignore
            }
        }

        String splitProperty = System.getProperty("split");
        if (splitProperty != null) {
            try {
                split = Boolean.valueOf(splitProperty);
            } catch (Exception e) {
                // ignore
            }
        }

        Path mainIndexPath = Paths.get(INDEX + "/original");
        Directory directory = FSDirectory.open(mainIndexPath);
        Path trainPath = Paths.get(INDEX + "/train");
        Path testPath = Paths.get(INDEX + "/test");
        Path cvPath = Paths.get(INDEX + "/cv");
        FSDirectory cv = null;
        FSDirectory test = null;
        FSDirectory train = null;
        DirectoryReader testReader = null;
        if (split) {
            cv = FSDirectory.open(cvPath);
            test = FSDirectory.open(testPath);
            train = FSDirectory.open(trainPath);
        }

        if (index) {
            delete(mainIndexPath);
            if (split) {
                delete(trainPath, testPath, cvPath);
            }
        }

        IndexReader reader = null;
        try {
            Analyzer analyzer = new StandardAnalyzer();
            if (index) {

                System.out.format("Indexing 20 Newsgroups...%n");

                long startIndex = System.currentTimeMillis();
                IndexWriter indexWriter = new IndexWriter(directory, new IndexWriterConfig(analyzer));

                buildIndex(new File(PREFIX + "/20n/20_newsgroups"), indexWriter);

                long endIndex = System.currentTimeMillis();
                System.out.format("Indexed %d pages in %ds %n", indexWriter.maxDoc(), (endIndex - startIndex) / 1000);

                indexWriter.close();

            }

            if (split && !index) {
                reader = DirectoryReader.open(train);
            } else {
                reader = DirectoryReader.open(directory);
            }

            if (index && split) {
                // split the index
                System.out.format("Splitting the index...%n");

                long startSplit = System.currentTimeMillis();
                DatasetSplitter datasetSplitter = new DatasetSplitter(0.1, 0);
                LeafReader originalIndex = SlowCompositeReaderWrapper.wrap(reader);
                datasetSplitter.split(originalIndex, train, test, cv, analyzer, false, "title", BODY_FIELD, SUBJECT_FIELD, CATEGORY_FIELD);
                reader.close();
                reader = DirectoryReader.open(train); // using the train index from now on
                long endSplit = System.currentTimeMillis();
                System.out.format("Splitting done in %ds %n", (endSplit - startSplit) / 1000);
            }
            final LeafReader ar = SlowCompositeReaderWrapper.wrap(reader);

            final long startTime = System.currentTimeMillis();

            List<Classifier<BytesRef>> classifiers = new LinkedList<>();
            classifiers.add(new KNearestNeighborClassifier(ar, new ClassicSimilarity(), analyzer, null, 1, 0, 0, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new ClassicSimilarity(), analyzer, null, 3, 0, 0, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new ClassicSimilarity(), analyzer, null, 3, 2, 4, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new LMDirichletSimilarity(), analyzer, null, 3, 1, 1, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new LMJelinekMercerSimilarity(0.3f), analyzer, null, 3, 1, 1, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new BM25Similarity(), analyzer, null, 3, 1, 1, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new DFRSimilarity(new BasicModelG(), new AfterEffectB(), new NormalizationH1()), analyzer, null, 3, 1, 1, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new DFRSimilarity(new BasicModelP(), new AfterEffectL(), new NormalizationH3()), analyzer, null, 3, 1, 1, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new IBSimilarity(new DistributionSPL(), new LambdaDF(), new Normalization.NoNormalization()), analyzer, null, 3, 1, 1, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new KNearestNeighborClassifier(ar, new IBSimilarity(new DistributionLL(), new LambdaTTF(), new NormalizationH1()), analyzer, null, 3, 1, 1, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new CachingNaiveBayesClassifier(ar, analyzer, null, CATEGORY_FIELD, BODY_FIELD));
            classifiers.add(new SimpleNaiveBayesClassifier(ar, analyzer, null, CATEGORY_FIELD, BODY_FIELD));

            int maxdoc;
            LeafReader testLeafReader;

            if (split) {
                testReader = DirectoryReader.open(test);
                testLeafReader = SlowCompositeReaderWrapper.wrap(testReader);
                maxdoc = testReader.maxDoc();
            } else {
                testLeafReader = null;
                maxdoc = reader.maxDoc();
            }

            System.out.format("Starting evaluation on %d docs...%n", maxdoc);

            ExecutorService service = Executors.newCachedThreadPool();
            List<Future<String>> futures = new LinkedList<>();
            for (Classifier<BytesRef> classifier : classifiers) {

                futures.add(service.submit(() -> {
                    ConfusionMatrixGenerator.ConfusionMatrix confusionMatrix;
                    if (split) {
                        confusionMatrix = ConfusionMatrixGenerator.getConfusionMatrix(testLeafReader, classifier, CATEGORY_FIELD, BODY_FIELD);
                    } else {
                        confusionMatrix = ConfusionMatrixGenerator.getConfusionMatrix(ar, classifier, CATEGORY_FIELD, BODY_FIELD);
                    }

                    final long endTime = System.currentTimeMillis();
                    final int elapse = (int) (endTime - startTime) / 1000;

                    System.out.println(confusionMatrix.getLinearizedMatrix().size() + " classes");

                    System.out.format("Generated confusion matrix:\n %s \n in %ds %n", confusionMatrix.toString(), elapse);

                    return classifier + " -> *** accuracy = " + confusionMatrix.getAccuracy() +
                            "; precision = " + confusionMatrix.getPrecision() +
                            "; recall = " + confusionMatrix.getRecall() +
                            "; f1-measure = " + confusionMatrix.getF1Measure() +
                            "; avgClassificationTime = " + confusionMatrix.getAvgClassificationTime() +
                            "; time = " + elapse + " (sec)\n ";
                }));

            }
            for (Future<String> f : futures) {
                System.out.println(f.get());
            }

            Thread.sleep(10000);
            service.shutdown();

        } finally {
            if (reader != null) {
                reader.close();
            }
            directory.close();
            if (test != null) {
                test.close();
            }
            if (train != null) {
                train.close();
            }
            if (cv != null) {
                cv.close();
            }
            if (testReader != null) {
                testReader.close();
            }
        }
    }

    private void delete(Path... paths) throws IOException {
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                Stream<Path> pathStream = Files.list(path);
                Iterator<Path> iterator = pathStream.iterator();
                while (iterator.hasNext()) {
                    Files.delete(iterator.next());
                }
            }
        }

    }


    void buildIndex(File indexDir, IndexWriter indexWriter)
            throws IOException {
        File[] groupsDir = indexDir.listFiles();
        for (File group : groupsDir) {
            if (groupsDir != null) {
                String groupName = group.getName();
                File[] posts = group.listFiles();
                if (posts != null) {
                    for (File postFile : posts) {
                        String number = postFile.getName();
                        NewsPost post = parse(postFile, groupName, number);
                        Document d = new Document();
                        d.add(new TextField(CATEGORY_FIELD,
                                post.getGroup(), Field.Store.YES));
                        d.add(new TextField(SUBJECT_FIELD,
                                post.getSubject(), Field.Store.YES));
                        d.add(new TextField(BODY_FIELD,
                                post.getBody(), Field.Store.YES));
                        indexWriter.addDocument(d);
                    }
                }
            }
        }
        indexWriter.commit();
    }

    private NewsPost parse(File postFile, String groupName, String number) throws IOException {
        StringBuilder body = new StringBuilder();
        String subject = "";
        FileInputStream stream = new FileInputStream(postFile);
        boolean inBody = false;
        for (String line : IOUtils.readLines(stream)) {
            if (line.startsWith("Subject:")) {
                subject = line.substring(8);
            } else {
                if (inBody) {
                    if (body.length() > 0) {
                        body.append("\n");
                    }
                    body.append(line);
                } else if (line.isEmpty() || line.trim().length() == 0) {
                    inBody = true;
                }
            }
        }
        return new NewsPost(body.toString(), subject, groupName, number);
    }

    private class NewsPost {
        private final String body;
        private final String subject;
        private final String group;
        private final String number;

        private NewsPost(String body, String subject, String group, String number) {
            this.body = body;
            this.subject = subject;
            this.group = group;
            this.number = number;
        }

        public String getBody() {
            return body;
        }

        public String getSubject() {
            return subject;
        }

        public String getGroup() {
            return group;
        }

        public String getNumber() {
            return number;
        }
    }
}