package searchengine.serviceRepositoryes;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.enums.STATUS;
import searchengine.model.Site;
import searchengine.repository.SiteRepository;
import java.util.List;
import java.util.Optional;

@Service
public class SiteService {
    @Autowired
    private SiteRepository siteRepository;


    public void saveSite(Site site) {
        siteRepository.save(site);
    }

    public boolean existsByName(String siteName) {
        return siteRepository.existsByName(siteName);
    }

    public Optional<Site> findByName(String siteName) {
        return siteRepository.findByName(siteName);
    }

    public boolean existsByStatus(STATUS status) {
        return siteRepository.existsByStatus(status);
    }

    public boolean isSiteIndexed(Site site) {
        return site.getStatus() == STATUS.INDEXED;
    }

    public void clear() {
        siteRepository.deleteAllInBatch();
    }

    public boolean isAnySiteIndexed() {
        return siteRepository.existsByStatus(STATUS.INDEXED);
    }

    public Site getSiteByUrl(String siteUrl) {
        if (siteRepository.existsByUrl(siteUrl)) {
            return siteRepository.findByUrl(siteUrl).get();
        } else {
            return null;
        }
    }

    public List<Site> getAllIndexedSites() {
        if (siteRepository.existsByStatus(STATUS.INDEXED)) {
            return siteRepository.findALLByStatus(STATUS.INDEXED);
        } else {
            return null;
        }
    }
}