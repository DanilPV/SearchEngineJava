package searchengine.servicesInterface;

import searchengine.dto.startIndexing.StartIndexingResponce;
import searchengine.dto.stopIndexing.StopIndexingResponce;

public interface StartStopIndexingSevice {
    StartIndexingResponce startIndexing();
    StopIndexingResponce stopIndexing();
}
