package examples.tinyfullexample.data.client;

import examples.tinyfullexample.data.common.BusinessDts;
import examples.tinyfullexample.data.server.DialogFacade;

public class DialogController {

    public DialogFacade facade;

    public void calculate(){
        BusinessDts data = facade.calculate();
        updateModel(data);
        refreshView();
    }

    void updateModel(BusinessDts data){

    }

    private void refreshView(){

    }
}
