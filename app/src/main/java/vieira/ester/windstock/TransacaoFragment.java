package vieira.ester.windstock;

import static android.app.Activity.RESULT_OK;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;

import java.util.Calendar;
import java.util.List;

import vieira.ester.windstock.databinding.FragmentTransacaoBinding;

public class TransacaoFragment extends Fragment {

    private FragmentTransacaoBinding transacaoBinding;
    TransacaoRepository transacaoRepository = new FirebaseTransacaoRepository();
    private static final int REQUEST_IMAGE_CAPTURE = 1;
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    String nomeUsuario;

    public TransacaoFragment() {
        super(R.layout.fragment_transacao);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            nomeUsuario = getArguments().getString("nome");
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        transacaoBinding = FragmentTransacaoBinding.bind(view);

        exibirData();

        transacaoBinding.buttonScanBarcode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    // A permissão já foi concedida, iniciar a câmera
                    startBarcodeScanner();
                } else {
                    // A permissão não foi concedida, solicitar ao usuário
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }
            }
        });

        transacaoBinding.buttonSalvar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String codigoItem = transacaoBinding.textCodProduto.getText().toString();
                String descricao = transacaoBinding.editTextDescricao.getText().toString();
                String data = transacaoBinding.editTextData.getText().toString();
                String tipo = transacaoBinding.spinnerTipoTransacao.getSelectedItem().toString();
                int qtdItens = Integer.parseInt(transacaoBinding.editTextQuantidade.getText().toString());
                String criadaPor = nomeUsuario;

                Transacao transacao = new Transacao(codigoItem, descricao, data, tipo, qtdItens, criadaPor);

                // Chamar o método para adicionar a transação
                transacaoRepository.adicionarTransacao(transacao, new OnCompleteListener<DocumentReference>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentReference> task) {
                        if (task.isSuccessful()) {
                            // Transação salva com sucesso
                            Toast.makeText(requireContext(), "Transação salva com sucesso!", Toast.LENGTH_SHORT).show();

                            // Redirecionar o usuário para a tela de histórico de transações
                            Navigation.findNavController(requireView()).navigate(R.id.action_transacaoFragment_to_historicoFragment);
                        } else {
                            Toast.makeText(requireContext(), "Erro ao salvar a transação", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });

    }

    private void exibirData() {
        Calendar calendar = Calendar.getInstance();
        int ano = calendar.get(Calendar.YEAR);
        int mes = calendar.get(Calendar.MONTH);
        int dia = calendar.get(Calendar.DAY_OF_MONTH);
        String dataPadrao = dia + "/" + (mes + 1) + "/" + ano;
        transacaoBinding.editTextData.setText(dataPadrao);

        transacaoBinding.editTextData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DatePickerDialog datePickerDialog = new DatePickerDialog(requireContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
                        String dataSelecionada = dayOfMonth + "/" + (month + 1) + "/" + year;
                        transacaoBinding.editTextData.setText(dataSelecionada);
                    }
                }, ano, mes, dia);

                datePickerDialog.show();
            }
        });
    }

    private void startBarcodeScanner() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(requireContext().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // A permissão foi concedida, chame o método para iniciar a câmera
                startBarcodeScanner();
            } else {
                // A permissão foi negada, trate o caso em que o usuário não concedeu a permissão
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bitmap bitmap = (Bitmap) data.getExtras().get("data");

            processImage(bitmap);
        }
    }

    private void processImage(Bitmap bitmap) {
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionBarcodeDetectorOptions options = new FirebaseVisionBarcodeDetectorOptions.Builder()
                .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_ALL_FORMATS)
                .build();

        FirebaseVisionBarcodeDetector detector = FirebaseVision.getInstance()
                .getVisionBarcodeDetector(options);

        detector.detectInImage(firebaseVisionImage)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionBarcode>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionBarcode> barcodes) {
                        // Process the detected barcodes
                        for (FirebaseVisionBarcode barcode : barcodes) {
                            Rect bounds = barcode.getBoundingBox();
                            Point[] corners = barcode.getCornerPoints();
                            String rawValue = barcode.getRawValue();
                            int valueType = barcode.getValueType();

                            transacaoBinding.textCodProduto.setText(rawValue);

                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        // Handle the failure
                        // ...
                    }
                });
    }
}
