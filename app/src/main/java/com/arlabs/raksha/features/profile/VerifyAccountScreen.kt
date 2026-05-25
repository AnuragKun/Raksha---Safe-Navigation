package com.arlabs.raksha.features.profile

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.arlabs.raksha.domain.util.Result
import com.arlabs.raksha.features.auth.AuthViewModel

@Composable
fun VerifyAccountScreen(
    navController: NavController,
    authViewModel: AuthViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    var phoneNumber by remember { mutableStateOf("") }
    var otpCode by remember { mutableStateOf(List(6) { "" }) }
    var isOtpVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequesters = remember { List(6) { FocusRequester() } }

    val PinkBrand = Color(0xFFE91E63)

    LaunchedEffect(authState) {
        when (val state = authState) {
            is Result.Success -> {
                val msg = state.data
                if (msg == "Code Sent") {
                    isOtpVisible = true
                    Toast.makeText(context, "OTP Sent Successfully", Toast.LENGTH_SHORT).show()
                } else if (msg == "Login Complete") {
                    Toast.makeText(context, "Phone Verified Successfully!", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            }
            is Result.Failure -> {
                val error = state.message
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                authViewModel.resetAuthState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .background(PinkBrand)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, 
                        contentDescription = "Back", 
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = "Verify Your Account",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Text(
                text = "Let's make your account secure.\nWe'll send you a code",
                fontSize = 18.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "+91",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, PinkBrand, RoundedCornerShape(28.dp))
                            .clip(RoundedCornerShape(28.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = phoneNumber,
                            onValueChange = { if (it.length <= 10) phoneNumber = it },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 16.dp),
                            textStyle = TextStyle(fontSize = 16.sp, color = Color.Black),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            decorationBox = { innerTextField ->
                                if (phoneNumber.isEmpty()) {
                                    Text("Enter your phone number", color = Color.Gray, fontSize = 14.sp)
                                }
                                innerTextField()
                            },
                            singleLine = true
                        )

                        Button(
                            onClick = { 
                                if (phoneNumber.length == 10) {
                                    val formatted = "+91$phoneNumber"
                                    authViewModel.sendOtp(formatted, context as Activity)
                                } else {
                                    Toast.makeText(context, "Enter a valid 10-digit number", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(130.dp),
                            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PinkBrand),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            if (authState is Result.Loading && !isOtpVisible) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Send SMS Code", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    if (isOtpVisible) {
                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Enter your 6-digit code sent to +91 $phoneNumber.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Verification Code",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (i in 0 until 6) {
                                BasicTextField(
                                    value = otpCode[i],
                                    onValueChange = { newValue ->
                                        if (newValue.length <= 1 && newValue.all { it.isDigit() }) {
                                            val newList = otpCode.toMutableList()
                                            newList[i] = newValue
                                            otpCode = newList
                                            if (newValue.isNotEmpty() && i < 5) {
                                                focusRequesters[i + 1].requestFocus()
                                            } else if (newValue.isEmpty() && i > 0) {
                                                focusRequesters[i - 1].requestFocus()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .focusRequester(focusRequesters[i])
                                        .border(2.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                        .background(Color.White, RoundedCornerShape(8.dp)),
                                    textStyle = TextStyle(
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = Color.Black
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                    cursorBrush = SolidColor(PinkBrand),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.Center) {
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { 
                                    val code = otpCode.joinToString("")
                                    if (code.length == 6) {
                                        authViewModel.verifyOtp(code)
                                    } else {
                                        Toast.makeText(context, "Enter the full 6-digit code", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PinkBrand),
                                modifier = Modifier
                                    .height(48.dp)
                                    .weight(1f)
                            ) {
                                if (authState is Result.Loading && isOtpVisible) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Verify Phone", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            TextButton(
                                onClick = { 
                                    val formatted = "+91$phoneNumber"
                                    authViewModel.sendOtp(formatted, context as Activity)
                                }
                            ) {
                                Text("Resend", color = Color.Gray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Why verify? Ensure trusted alerts and\naccount recovery.",
                fontSize = 16.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            )
        }
    }
}
