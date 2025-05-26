package in.co.kitree.services;

import com.amazonaws.services.lambda.invoke.LambdaFunction;
//import software.amazon.awssdk.services.lambda.LambdaClient;
import in.co.kitree.pojos.PythonLambdaResponseBody;
import in.co.kitree.pojos.PythonLambdaEventRequest;

public interface PythonLambdaService {
  @LambdaFunction(functionName="certgen")
  PythonLambdaResponseBody invokePythonLambda(PythonLambdaEventRequest request);
}