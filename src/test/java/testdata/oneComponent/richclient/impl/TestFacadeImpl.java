package testdata.oneComponent.richclient.impl;

import javax.validation.constraints.NotNull;

import com.pty4j.util.Pair;

import testdata.oneComponent.dto.TestDataDto;
import testdata.oneComponent.entity.TestData;
import testdata.oneComponent.richclient.TestFacade;
import testdata.oneComponent.service.TestDataService;

public class TestFacadeImpl implements TestFacade {

    @NotNull
    public TestDataService service;
    public TestDataDtoMapper mapper;
    public TestTraceMapperImpl traceMapper;

    @Override
    public TestDataDto load() {
        return mapper.mapToTestDataDto(service.load());
    }

    @Override
    public void save(TestDataDto traceData) {
        TestData traceDirect = new TestData();
        Pair<TestData, TestData> traceIndirect = Pair.create(new TestData(), new TestData());
        traceData.text = "test string facade";
        TestData traceInFacade = null;
        traceInFacade = traceDirect;
        traceInFacade = traceIndirect.first;
        traceInFacade = traceMapper.mapTracingToEntity(traceData);
        service.save(mapper.mapToTestDataDs(traceData));
    }
}
