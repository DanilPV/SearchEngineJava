package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.startIndexing.StartIndexingResponce;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.stopIndexing.StopIndexingResponce;
import searchengine.services.SearchService;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final StatisticsService statisticsService;
    private final IndexingService startStopIndexingService;
    private final SearchService searchService;


    @GetMapping("/statistics")
    public StatisticsResponse statistics() {
        return statisticsService.getStatistics();
    }

    @GetMapping("/startIndexing")
    public StartIndexingResponce startIndexing() {
        return startStopIndexingService.preIndexing(null);
    }

    @PostMapping("/indexPage")
    public StartIndexingResponce indexPage(@RequestBody String url) {
        return startStopIndexingService.preIndexing(url);
    }


    @GetMapping("/stopIndexing")
    public StopIndexingResponce stopIndexing() {
        return startStopIndexingService.stopIndexing();
    }


    @GetMapping("/search{query}{site}{offset}{limit}")
    public SearchResponse search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        return searchService.search(query, site, offset, limit);

    }

}
