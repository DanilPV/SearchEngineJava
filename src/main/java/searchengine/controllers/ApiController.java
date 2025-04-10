package searchengine.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.indexPage.IndexPageResponce;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.startIndexing.StartIndexingResponce;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.stopIndexing.StopIndexingResponce;
import searchengine.servicesInterface.IndexPageService;
import searchengine.servicesInterface.SearchService;
import searchengine.servicesInterface.StartStopIndexingSevice;
import searchengine.servicesInterface.StatisticsService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final StatisticsService statisticsService;
    private StartStopIndexingSevice startStopIndexingSevice;
    private IndexPageService indexPageService;
    private SearchService searchService;

    public ApiController(StatisticsService statisticsService, StartStopIndexingSevice startStopIndexingSevice, IndexPageService indexPageService, SearchService searchService)   {

        this.statisticsService = statisticsService;
        this.startStopIndexingSevice = startStopIndexingSevice;
        this.indexPageService = indexPageService;
        this.searchService = searchService;

    }


    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public StartIndexingResponce startIndexing() {
        return  startStopIndexingSevice.startIndexing();
    }

    @GetMapping("/stopIndexing")
    public StopIndexingResponce stopIndexing() {
        return startStopIndexingSevice.stopIndexing();
    }


    @PostMapping("/indexPage")
    public IndexPageResponce indexPage(@RequestBody String url)   {
        return  indexPageService.indexPage(url) ;
    }

    @GetMapping("/search{query}{site}{offset}{limit}")
    public SearchResponse search (
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        return  searchService.search(query,site,offset,limit) ;

    }

}
