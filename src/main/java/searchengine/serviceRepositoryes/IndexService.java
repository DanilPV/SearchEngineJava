package searchengine.serviceRepositoryes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.function.Functions;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static searchengine.function.Functions.partitionList;

@Service
public class IndexService {

    public static ConcurrentHashMap<String, Index> allIndex = new ConcurrentHashMap<>();

    @Autowired
    private IndexRepository indexRepository;


    @Transactional
    public void saveAllIndexSite(Site site) {

        try {

            // 1. Подготовка данных для сохранения
            List<Index> indexesToSave = allIndex.values().stream()
                    .filter(index -> index.getPage().getSite().equals(site))
                    .collect(Collectors.toList());

            // 2. Параллельное сохранение с использованием ForkJoinPool
            int batchSize = 1500; // Размер батча
            List<List<Index>> batches = Functions.partitionList(indexesToSave, batchSize);

            ForkJoinPool customThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
            try {
                customThreadPool.submit(() ->
                        batches.parallelStream().forEach(batch -> {
                            indexRepository.saveAll(batch);
                            indexRepository.flush(); // Принудительная запись батча
                        })
                ).get(); // Ожидаем завершения всех задач
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

    public Index getIndex(Page page, Lemma lemma) {
        if (page != null && lemma != null) {
            return indexRepository.findByPageAndLemma(page, lemma);
        } else {
            return null;
        }
    }
}
