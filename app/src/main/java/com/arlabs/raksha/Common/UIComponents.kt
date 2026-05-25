package com.arlabs.raksha.Common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import com.arlabs.raksha.R

@Composable
fun GradientBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val gradientColors: List<Color> = listOf(
        colorResource(id = R.color.pink1),
        colorResource(id = R.color.pink2),
        colorResource(id = R.color.pink3)
    )

    Box(
        modifier = modifier
            .background(brush = Brush.verticalGradient(colors = gradientColors)),
        content = content
    )
}



class TopCurveShape: Shape{
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ) : Outline {
        val path = Path().apply {
            val curveStartHeight = size.height * 0.15f
            moveTo(0f,size.height)
            lineTo(0f,curveStartHeight)
            quadraticTo(x1 = size.width / 2,      // Control point X: middle
                y1 = curveStartHeight,    // Control point Y: bottom of the curve
                x2 = size.width,          // End point X: right edge
                y2 = 0f)
            lineTo(size.width,size.height)
            close()
        }
    return Outline.Generic(path)
    }
}

// Aesthetic Theme Constants
val AestheticCornerRadius = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
val AestheticHorizontalPadding = 20.dp
val AestheticTransparentWhite = Color.White.copy(alpha = 0.3f)
val AestheticTransparentWhiteStrong = Color.White.copy(alpha = 0.7f)

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AestheticTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}
) {
    androidx.compose.material3.CenterAlignedTopAppBar(
        title = {
            androidx.compose.material3.Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = Color.White
            )
        },
        actions = actions,
        colors = androidx.compose.material3.TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White
        ),
        modifier = modifier
    )
}

@Composable
fun GlassmorphismCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(AestheticCornerRadius)
            .background(AestheticTransparentWhite)
            .shadow(
                elevation = 4.dp,
                shape = AestheticCornerRadius,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.1f)
            ),
        content = content
    )
}