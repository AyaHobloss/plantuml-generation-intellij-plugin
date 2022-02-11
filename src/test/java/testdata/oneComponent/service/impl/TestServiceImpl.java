package testdata.oneComponent.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import testdata.oneComponent.domain.TestManager;
import testdata.oneComponent.entity.ds.TestDataDs;
import testdata.oneComponent.entity.mapper.TestDataMapper;
import testdata.oneComponent.service.TestDataService;

public class TestServiceImpl implements TestDataService {

    public final String finalConstant = "[1]";
    public String noneFinalConstant = "[0..1]";

    @Autowired
    public TestManager manager;

    @Autowired(required = false)
    public TestDataMapper mapper;

    @Override
    public TestDataDs load() {
        return mapper.mapToTestDataDs(manager.load());
    }

    @Override
    public TestDataDs save(TestDataDs data) {
        TestUtil.testUtilMethod();
        return mapper.mapToTestDataDs(manager.save(mapper.mapToTestData(data)));
    }
}
