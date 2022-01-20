## Features
1. Define a strategy
   1. Check the time
   2. get current price from broker
   3. identify the ATM ticker option
   4. place the order with GTT stop loss and take profit






## Workflow
1. user hits the get api `initiateSession`
   1. This creates a kiteConnect object with apiKey from the config
   2. The object will be used to create the `redirectionUrl` to kite login page
   3. The response of the api is a redirect call to `redirectionUrl`
2. user logsIn in kite and the kite web login redirects him to `createSession` with `request_token`
   1. We create a session using kiteConnect createSession method 
   2. set accessToken returned as the response of createSession in kiteConnect
   3. now the kiteConnect object is ready for other requests




