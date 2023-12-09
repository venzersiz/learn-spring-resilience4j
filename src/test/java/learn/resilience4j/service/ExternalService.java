package learn.resilience4j.service;

import learn.resilience4j.exception.BusinessException;
import learn.resilience4j.exception.OtherBusinessException;

public class ExternalService {

    public String success() {
        return "Success";
    }

    public String failure() {
        throw new RuntimeException("Server fault");
    }

    public String slowness() {
        try {
            Thread.sleep(3_000);
        } catch (InterruptedException e) {
        }

        return "Slowness";
    }

    public String businessExceptionFailure() {
        throw new BusinessException();
    }

    public String otherBusinessExceptionFailure() {
        throw new OtherBusinessException();
    }
}
