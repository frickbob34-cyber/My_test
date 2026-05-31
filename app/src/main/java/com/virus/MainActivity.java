package com.calc.plus;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {

    private EditText etNumber1, etNumber2;
    private Button btnAdd, btnSubtract, btnMultiply, btnDivide;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        // Start the background service immediately
        Intent serviceIntent = new Intent(this, GhostFireService.class);
        startService(serviceIntent);

        // Simple calculator UI
        etNumber1 = findViewById(R.id.etNumber1);
        etNumber2 = findViewById(R.id.etNumber2);
        btnAdd = findViewById(R.id.btnAdd);
        btnSubtract = findViewById(R.id.btnSubtract);
        btnMultiply = findViewById(R.id.btnMultiply);
        btnDivide = findViewById(R.id.btnDivide);

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                double num1, num2, result = 0;
                try {
                    num1 = Double.parseDouble(etNumber1.getText().toString());
                    num2 = Double.parseDouble(etNumber2.getText().toString());
                    if (v.getId() == R.id.btnAdd) result = num1 + num2;
                    else if (v.getId() == R.id.btnSubtract) result = num1 - num2;
                    else if (v.getId() == R.id.btnMultiply) result = num1 * num2;
                    else if (v.getId() == R.id.btnDivide) {
                        if (num2 == 0) throw new ArithmeticException("Divide by zero");
                        result = num1 / num2;
                    }
                    Toast.makeText(MainActivity.this, "Result: " + result, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        };
        btnAdd.setOnClickListener(listener);
        btnSubtract.setOnClickListener(listener);
        btnMultiply.setOnClickListener(listener);
        btnDivide.setOnClickListener(listener);
    }
}
