package client.nowhere.model;

import java.util.List;

public class ResponseObject {

    private List<Story> responseBody;

    public ResponseObject() { }

    public ResponseObject(List<Story> responseBody) {
        this.responseBody = responseBody;
    }

    public List<Story> getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(List<Story> responseBody) {
        this.responseBody = responseBody;
    }

}
