package examples.tinyfullexample.data.server;

import examples.tinyfullexample.data.common.BusinessDts;

public class DialogFacade {
    public BusinessService service;
    public BusinessMapper mapper;

    public BusinessDts calculate() {
        BusinessEntity entity = service.calculate();

        return mapper.toDts(entity);
    }

}
