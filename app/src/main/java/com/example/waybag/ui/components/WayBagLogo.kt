package com.example.waybag.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.example.waybag.R
import com.example.waybag.ui.theme.Premium_Gray

@Composable
fun WayBagLogo(
    modifier: Modifier = Modifier,
    color: Color = Premium_Gray
) {
    Box(
        modifier = modifier.clip(CircleShape), 
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.waybag_logo),
            contentDescription = "WayBag Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}
