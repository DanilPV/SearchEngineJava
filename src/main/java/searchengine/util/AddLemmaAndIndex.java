package searchengine.util;


import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class AddLemmaAndIndex {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    private static ConcurrentHashMap<String, Lemma> addLemmas = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Index> allIndex = new ConcurrentHashMap<>();

    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ"};
    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }


    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-я\\s])", " ")
                .trim()
                .split("\\s+");
    }

    public  TreeMap<String, Integer> extractLemmasFromString(String text) throws IOException {

        String[] words = arrayContainsRussianWords(text);
        TreeMap<String, Integer> lemmas = new TreeMap<>();
        LuceneMorphology luceneMorphology = new RussianLuceneMorphology();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            if (anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }

            List<String> normalForms =   luceneMorphology.getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);


            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.getOrDefault(normalWord, 0) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }

        return lemmas;
    }



    public void addAllLemma(TreeMap<String, Integer> lemaList, Page page) {

        List<Lemma> newLemmas = new ArrayList<>();
        try {


            lemaList.forEach((lemmaName, frequency) -> {
                Lemma lemma;
                if (lemmaRepository.findByLemmaAndSite(lemmaName, page.getSite()) == null) {

                    lemma = new Lemma();
                    lemma.setSite(page.getSite());
                    lemma.setLemma(lemmaName);
                    newLemmas.add(lemma);

                } else {
                    lemma = lemmaRepository.findByLemmaAndSite(lemmaName, page.getSite());
                }

                String key = lemmaName + "|" + page.getSite().getId();

                addLemmas.compute(key, (k, existingLemma) -> {
                    if (existingLemma != null) {
                        existingLemma.setFrequency(existingLemma.getFrequency() + 1);
                        return existingLemma;
                    } else {
                        lemma.setFrequency(1);
                        return lemma;
                    }
                });

                Index index = new Index();
                index.setPage(page);
                index.setRank(frequency);
                index.setLemma(addLemmas.get(key));

                allIndex.put(index.getPage().getPath() + index.getLemma().getLemma(), index);
            });

        } catch (Exception e) {
            System.out.println(" Ошибка при сохранении лемм:" + e.getMessage());
        }


    }

    public void saveAllLemmaToBD(Site site) {

        List<Lemma> lemmasToSave = addLemmas.values().stream()
                .filter(lemma -> lemma.getSite().equals(site))
                .collect(Collectors.toList());

        int batchSize = 1500;
        List<List<Lemma>> batches = partitionList(lemmasToSave, batchSize);

        ForkJoinPool customThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());

        try {
            customThreadPool.submit(() ->
                    batches.parallelStream().forEach(batch -> {
                        lemmaRepository.saveAll(batch);
                        lemmaRepository.flush();
                    })
            ).get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            customThreadPool.shutdown();
        }


        try {
            addLemmas.values().removeIf((lemma -> lemma.getSite().equals(site)));
        } catch (Exception e) {
            System.out.println(" Ошибка при удалении лемм:" + e.getMessage());
        }

        System.out.println("Леммы сайта " + site.getName() + " сохранены");

        if (!batches.isEmpty()) {

            saveAllIndexToBD(site);
        }


    }




    @Transactional
    public void saveAllIndexToBD(Site site) {

        try {

            List<Index> indexesToSave = allIndex.values().stream()
                    .filter(index -> index.getPage().getSite().equals(site))
                    .collect(Collectors.toList());

            int batchSize = 1500;
            List<List<Index>> batches = partitionList(indexesToSave, batchSize);

            ForkJoinPool customThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            try {
                customThreadPool.submit(() ->
                        batches.parallelStream().forEach(batch -> {
                            indexRepository.saveAll(batch);
                            indexRepository.flush();
                        })
                ).get();

            } catch (Exception e) {
                System.out.println(" Ошибка при сохранении индексов:" + e.getMessage());
            } finally {
                customThreadPool.shutdown();
            }

            try {
                allIndex.values().removeIf(index -> index.getPage().getSite().equals(site));
            } catch (Exception e) {
                System.out.println(" Ошибка при удалении индексов:" + e.getMessage());
            }

            System.out.println("Индексы сайта " + site.getName() + " сохранены");
        } catch (Exception e) {
            System.out.println(" Ошибка при сохранении индексов:" + e.getMessage());
        }


    }


    public static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        return IntStream.range(0, (list.size() + batchSize - 1) / batchSize)
                .mapToObj(i -> list.subList(i * batchSize, Math.min((i + 1) * batchSize, list.size())))
                .collect(Collectors.toList());
    }


}