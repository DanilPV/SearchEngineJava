package searchengine.serviceRepositoryes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PageService {

    @Autowired
    private PageRepository pageRepository;

    public void savePage(Page page) {
        pageRepository.save(page);
    }

    public int getPageCountBySite(Site siteUrl) {
        if (pageRepository.existsBySite(siteUrl)) {
            return pageRepository.findBySite(siteUrl).size();
        } else {
            return 0;
        }
    }

    public int getTotalPageCount() {
        return pageRepository.findAll().size();
    }

    public HashSet<Page> findPagesByLemma(Lemma firstLemma) {
        return new HashSet<>(pageRepository.findAllPageByLemma(firstLemma));
    }

    public String getPageTitle(Page page) {

        if (page.getContent() == null) {
            return "Без заголовка";
        }
        Pattern pattern = Pattern.compile("<title>(.*?)</title>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(page.getContent());
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "Без заголовка";
    }


}