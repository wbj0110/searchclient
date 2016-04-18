package search.solr.client.control;


import org.springframework.web.bind.annotation.*;
import search.solr.client.entity.searchinterface.FilterAttributeSearchResult;
import search.solr.client.entity.searchinterface.NiNi;
import search.solr.client.entity.searchinterface.parameter.SearchByKeyWordsParam;
import search.solr.client.entity.searchinterface.parameter.TestSimple;
import search.solr.client.searchInterface.SearchInterface;


@RestController
@RequestMapping("/search")
public class SearchController {


   /* public NiNi getCategoryIdsByKeyWords(@RequestParam(value = "userid", defaultValue = "null") String userid) {
        return SearchInterface.searchByKeywords("mergescloud", "screencloud", "圆筒", 363, null, null, 0, 10);
    }*/

    @RequestMapping(value = "/keywords", method = RequestMethod.POST)
    public NiNi searchByKeywords(@RequestBody final SearchByKeyWordsParam searchByKeywords) {
        String collection = searchByKeywords.getCollection();
        String attrCollection = searchByKeywords.getAttrCollection();
        Integer cityId = searchByKeywords.getCityId();
        if (collection == null || attrCollection == null || cityId == null) {
            NiNi nini = new NiNi();
            nini.setCode(-1);
            nini.setMsg("failed");
            return nini;
        } else
            return SearchInterface.searchByKeywords(collection, attrCollection, searchByKeywords.getKeyWords(), cityId, searchByKeywords.getFilters(), searchByKeywords.getSorts(), 0, 10);

    }

    @RequestMapping(value = "/test", method = RequestMethod.POST)
    public NiNi test(@RequestBody final TestSimple testSimple) {
        return SearchInterface.searchByKeywords("mergescloud", "screencloud", "圆筒", 363, null, null, 0, 10);
    }
}