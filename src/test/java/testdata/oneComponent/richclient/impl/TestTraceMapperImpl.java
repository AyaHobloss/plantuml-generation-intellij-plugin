package testdata.oneComponent.richclient.impl;

import testdata.oneComponent.dto.TestDataDto;
import testdata.oneComponent.entity.SubTestData;
import testdata.oneComponent.entity.TestData;

public class TestTraceMapperImpl {

    public TestData mapTracingToEntity(TestDataDto traceDto){
        TestData entity = new TestData();
        entity.text = traceDto.text;
        entity.subObject = new SubTestData();
        entity.subObject.subText = traceDto.subText;

        return entity;
    }


}
