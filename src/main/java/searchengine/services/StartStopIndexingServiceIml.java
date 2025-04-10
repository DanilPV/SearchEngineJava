package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import searchengine.classesError.RestException;
import searchengine.config.SitesListConfig;
import searchengine.dto.startIndexing.StartIndexingResponce;
import searchengine.dto.stopIndexing.StopIndexingResponce;
import searchengine.function.StartIndexingFunction;
import searchengine.enums.StatusIndexing;
import searchengine.serviceRepositoryes.SiteService;
import searchengine.servicesInterface.StartStopIndexingSevice;

@Service
@RequiredArgsConstructor
public class StartStopIndexingServiceIml implements StartStopIndexingSevice {

    private final SitesListConfig sites;

    @Autowired
    private StartIndexingFunction startIndexingFunction;

    @Autowired
    private SiteService siteService;



    @Override
    public StartIndexingResponce startIndexing() {

        StartIndexingResponce result = new StartIndexingResponce(false);
        StatusIndexing indexation = StartIndexingFunction.isIndexing;

        if (indexation == StatusIndexing.INDEXING) {
            throw new RestException(false, "Индексация уже запущена.", HttpStatus.OK);
        }
        if (indexation == StatusIndexing.INTERRUPTION) {
            throw new RestException(false, "Индексация в процессе остановки.", HttpStatus.OK);
        }


        siteService.clear();

        startIndexingFunction.setSites(sites);
        startIndexingFunction.setStartPage(null);
        startIndexingFunction.startIndexing();

        if (StartIndexingFunction.isIndexing == StatusIndexing.INDEXING) {
            result.setResult(true);
        } else {
            throw new RestException(false, "Не удалось запустить индексацию.", HttpStatus.OK);
        }

        return result;
    }


    @Override
    public StopIndexingResponce stopIndexing() {

        StopIndexingResponce result = new StopIndexingResponce(false);
        StatusIndexing indexation = StartIndexingFunction.isIndexing;

        if (indexation == StatusIndexing.STOP) {
            throw new RestException(false, "Индексация не запущена.", HttpStatus.OK);
        } else if (indexation == StatusIndexing.INTERRUPTION) {
            throw new RestException(false, "Индексация останавливается.", HttpStatus.OK);
        }

        try {
            if (startIndexingFunction.stopIndexing() == StatusIndexing.INDEXING) {
                throw new RestException(false, "Не удалось становить индексацию.", HttpStatus.OK);
            } else {
                result.setResult(true);
            }
        } catch (Exception ignored) {
        }

        return result;
    }

}
