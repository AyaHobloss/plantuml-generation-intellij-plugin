package examples.tinyfullexample.data.server;

public class BusinessService {
    public DataRepository repository;

    public BusinessEntity calculate() {
        BusinessEntity entity = repository.load();

        calculation(entity);

        return entity;
    }

    private void calculation(BusinessEntity entity){

    }

}
