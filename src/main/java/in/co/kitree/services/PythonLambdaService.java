package in.co.kitree.services;

import in.co.kitree.pojos.PythonLambdaEventRequest;
import in.co.kitree.pojos.PythonLambdaResponseBody;

public interface PythonLambdaService {
    PythonLambdaResponseBody invokePythonLambda(PythonLambdaEventRequest request);
}