package com.wordnik.swagger.testframework;

import com.wordnik.swagger.codegen.config.java.JavaDataTypeMappingProvider;
import com.wordnik.swagger.runtime.annotations.MethodArgumentNames;
import com.wordnik.swagger.codegen.config.common.CamelCaseNamingPolicyProvider;
import com.wordnik.swagger.runtime.common.APIInvoker;
import com.wordnik.swagger.runtime.common.ApiKeyAuthTokenBasedSecurityHandler;
import com.wordnik.swagger.runtime.exception.APIException;
import com.wordnik.swagger.runtime.exception.APIExceptionCodes;
import org.apache.commons.beanutils.BeanUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Instance of this class runs single test case
 * User: ramesh
 * Date: 4/22/11
 * Time: 7:32 AM
 */
public class JavaTestCaseExecutor {

    private CamelCaseNamingPolicyProvider namingPolicyProvider = new CamelCaseNamingPolicyProvider();
    private JavaDataTypeMappingProvider datatypeMppingProvider = new JavaDataTypeMappingProvider();

    /**
     * Follow the following argument pattern
     * Arguments in calling this method:
     * ApiServerURL
     *
     * @param args
     * @throws Exception
     */
	public static void main(String[] args) throws Exception {


		JavaTestCaseExecutor runner = new JavaTestCaseExecutor();
        String apiServer = args[0];
        String servicePackageName = args[1];
        String apiKey = args[2];
        String authToken = args[3];
        String resource = args[4];
        String httpMethod = args[5];
        String suggestedMethodName = args[6];
        Map<String, String> queryAndPathParameters = new HashMap<String, String>();
        String postData = null;
        if(args.length > 7 && args[7].length() > 0){
            String[] qpTuple = args[7].split("~");
            for(String tuple: qpTuple){
                String[] nameValue = tuple.split("=");
                queryAndPathParameters.put(nameValue[0], nameValue[1]);
            }
        }
        if(args.length > 8 ){
            postData = args[8];
        }
        queryAndPathParameters.put("authToken", authToken);

        ApiKeyAuthTokenBasedSecurityHandler securityHandler = new ApiKeyAuthTokenBasedSecurityHandler(apiKey, authToken);
        APIInvoker.initialize(securityHandler, apiServer, true);

        runner.executeTestCase(resource, servicePackageName, suggestedMethodName, queryAndPathParameters, postData);

	}

    private void executeTestCase(String resourceName, String servicePackageName, String suggestedName,
                                 Map<String, String> queryAndPathParameters, String postData) {

        String className = namingPolicyProvider.getServiceName(resourceName);
        String methodName = suggestedName;
        //3
        try {
            Class apiClass = Class.forName(servicePackageName + "." + className);
            Method[] methods = apiClass.getMethods();
            Method methodToExecute = null;
            for(Method method : methods){
                if(method.getName().equals(methodName)){
                    methodToExecute = method;
                    break;
                }
            }

            if(methodToExecute != null) {
                //4
                Object[] arguments = populateArgumentsForTestCaseExecution(methodToExecute, queryAndPathParameters,
                                                           postData, className, resourceName);
                Object output = null;
                if(arguments != null && arguments.length > 0){
                    //5
                    output = methodToExecute.invoke(null, arguments);
                }else{
                    //5
                    output = methodToExecute.invoke(null);
                }
                //6
                System.out.println("SUCCESS");
                System.out.println(APITestRunner.convertObjectToJSONString(output));

            }
        }catch(APIException e){
            StringWriter sWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(sWriter);
            e.printStackTrace(writer);
            System.out.println(sWriter.getBuffer().toString());
            System.out.println(e.getMessage());
            System.out.println("ERROR");
            try{
                System.out.println(APITestRunner.convertObjectToJSONString(e));
            }catch(Exception ex){
                ex.printStackTrace();
            }
        } catch(Exception e){
            StringWriter sWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(sWriter);
            e.printStackTrace(writer);
            System.out.println(sWriter.getBuffer().toString());
            e.printStackTrace();
            System.out.println("ERROR");
            try{
                APIException apiException = new APIException(APIExceptionCodes.SYSTEM_EXCEPTION,
                        e.getMessage());
                System.out.println(APITestRunner.convertObjectToJSONString(apiException));
            }catch(Exception ex){
                ex.printStackTrace();
            }
        }
    }

    /**
     * Gets the list of input query and path parameters and post data vlues and covenrt them to arguments that
     * can be used for calling the method. This logic will be different in each driver language depends on how method
     * input arguments are created.
     */
    private Object[] populateArgumentsForTestCaseExecution(Method methodToExecute, Map<String, String> queryAndPathParameters,
                                                           String postData, String serviceName, String resourcePath) throws Exception {
        MethodArgumentNames argNames = methodToExecute.getAnnotation(MethodArgumentNames.class);
        String[] argNamesArray = null;
        if(argNames != null && argNames.value().length() > 0) {
            argNamesArray = argNames.value().split(",");
        }
        Class[] argTypesArray = methodToExecute.getParameterTypes();
        Object output = null;
        String inputClassName = namingPolicyProvider.getInputObjectName(serviceName, resourcePath);

        if(argNamesArray != null && argNamesArray.length > 0){
            Object[] arguments = new Object[argNamesArray.length];

            for(int i=0; i < argNamesArray.length; i++){
                Object argument = null;
                //if the method takes input model instead of individual arguments, convert individual arguments into input model object
                if(argTypesArray[i].getName().equalsIgnoreCase(inputClassName)){
                    argument = populateInputModelObject(argTypesArray[i], queryAndPathParameters);
                }else if(datatypeMppingProvider.isPrimitiveType(argTypesArray[i].getName())){
                    argument = queryAndPathParameters.get(argNamesArray[i].trim());
                }else if (argNamesArray[i].trim().equals(APITestRunner.POST_PARAM_NAME)){
                    argument = APITestRunner.convertJSONStringToObject(postData, argTypesArray[i]);
                }else{
                    argument = APITestRunner.convertJSONStringToObject(queryAndPathParameters.get(argNamesArray[i].trim()), argTypesArray[i]);
                }
                arguments[i] = argument;
            }
            return arguments;
        }
        return null;
    }

	/**
	 * Populates the swagger input model object.
     *
     * Input model is created when number of inputs to a method exceed certain limit.
	 * @param inputDefinitions
	 * @return
	 */
	private Object populateInputModelObject(Class swaggerInputClass, Map<String, String> inputDefinitions) throws Exception {
		Object object =  swaggerInputClass.getConstructor().newInstance();
		Method[] methods = swaggerInputClass.getMethods();
		for(Method method : methods){
			if(method.getName().startsWith("get")){
				String methodName = method.getName();
				String fieldName = methodName.substring(3);
				fieldName = namingPolicyProvider.applyMethodNamingPolicy(fieldName);
				Object value = inputDefinitions.get(fieldName);
				BeanUtils.setProperty(object, fieldName, value);
			}
		}
		return object;
	}    

}
