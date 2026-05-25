package com.arlabs.raksha.features.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.arlabs.raksha.Common.GradientBox
import com.arlabs.raksha.Common.TopCurveShape
import com.arlabs.raksha.R
import com.arlabs.raksha.navigation.Routes
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.arlabs.raksha.domain.util.Result
import com.arlabs.raksha.util.GoogleSignInHelper
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun AuthenticationScreen(
    navHostController: NavHostController,
    authViewModel: AuthViewModel = hiltViewModel()
) {

    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val authState by authViewModel.authState.collectAsState()

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d("AuthScreen", "Google Sign-In result code: ${result.resultCode}")
        // Always try to extract account/error from intent regardless of result code
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                Log.d("AuthScreen", "Account obtained: ${account.email}, calling signInWithGoogle")
                authViewModel.signInWithGoogle(account)
            } else {
                Log.e("AuthScreen", "Account is null")
                showError = true
                errorMessage = "Google sign in failed: Account is null"
            }
        } catch (e: ApiException) {
            // Status codes: 10=DEVELOPER_ERROR, 12500=SIGN_IN_CANCELLED, 12501=SIGN_IN_CURRENTLY_IN_PROGRESS
            // 7=NETWORK_ERROR, 8=INTERNAL_ERROR
            Log.e("AuthScreen", "Google Sign-In FAILED: statusCode=${e.statusCode}, message=${e.message}", e)
            showError = true
            errorMessage = "Sign in failed (code ${e.statusCode}): ${e.message}"
        }
    }

    LaunchedEffect(authState) {
        Log.d("AuthScreen", "authState changed to: $authState")
        when (val currentState = authState) {
            is Result.Success -> {
                Log.d("AuthScreen", "Success state: ${currentState.data}")
                if (currentState.data == "Login Complete") {
                    Log.d("AuthScreen", "Navigating to MainScreen")
                    navHostController.navigate(Routes.MainScreen){
                        popUpTo(Routes.AuthenticationScreen) {inclusive = true}
                    }
                }
            }
            is Result.Failure -> {
                Log.e("AuthScreen", "Failure state: ${currentState.message}")
                showError = true
                errorMessage = currentState.message
            }
            Result.Idle, Result.Loading -> {
                Log.d("AuthScreen", "State: $currentState")
            }
        }
    }

    // Show error as Snackbar
    LaunchedEffect(showError) {
        if (showError) {
            scope.launch {
                snackbarHostState.showSnackbar(errorMessage)
            }
            showError = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // Background Image or Gradient for premium feel
            Image(
                painter = painterResource(id = R.drawable.ic_raksha_logo),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 100.dp)
                    .size(200.dp)
                    .clip(RoundedCornerShape(20.dp)) // subtle soft edges
            )

            GradientBox(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Welcome Back",
                                style = TextStyle(
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                "Your safety companion for every journey.",
                                style = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.Gray
                                ),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    val googleSignInClient = GoogleSignInHelper.getGoogleSignInClient(context)
                                    val signInIntent = googleSignInClient.signInIntent
                                    googleSignInLauncher.launch(signInIntent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Black,
                                    contentColor = Color.White
                                ),
                                elevation = ButtonDefaults.buttonElevation(
                                    defaultElevation = 6.dp,
                                    pressedElevation = 2.dp
                                )
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_google),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    "Continue with Google",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
            
            if (authState is Result.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
        }
    }
}


//@Composable
//@Preview(showSystemUi = true)
//fun AuthScreenPreview(){
//    AuthenticationScreen()
//}