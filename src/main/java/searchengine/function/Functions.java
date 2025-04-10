package searchengine.function;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Functions {

    // Вспомогательный метод для разбиения на батчи
    public static <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        return IntStream.range(0, (list.size() + batchSize - 1) / batchSize)
                .mapToObj(i -> list.subList(i * batchSize, Math.min((i + 1) * batchSize, list.size())))
                .collect(Collectors.toList());
    }

}
