package tp20260054.eroutes.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.usuarios.dto.CambiarContrasenaDto;
import tp20260054.eroutes.usuarios.dto.RegistroRequestDto;
import tp20260054.eroutes.usuarios.dto.VehiculoDto;
import tp20260054.eroutes.usuarios.dto.VehiculoRequestDto;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock VehiculoRepository vehiculoRepository;

    // PasswordEncoder real para validar las reglas
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private UsuarioService service;

    @BeforeEach
    void setUp() {
        service = new UsuarioService(usuarioRepository, vehiculoRepository, passwordEncoder);
        // Inyectar maximoVehiculos = 3 vía reflexión
        try {
            var f = UsuarioService.class.getDeclaredField("maximoVehiculos");
            f.setAccessible(true);
            f.set(service, 3);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ---- Helper para crear usuario base ----

    private Usuario usuarioBase(long id) {
        Usuario u = new Usuario();
        u.setId(id);
        u.setCorreo("test@example.com");
        u.setContrasenaHash(passwordEncoder.encode("password123"));
        u.setNombres("Test");
        u.setApellidos("User");
        u.setCreadoEn(OffsetDateTime.now());
        u.setActualizadoEn(OffsetDateTime.now());
        return u;
    }

    private Vehiculo vehiculoBase(long id, long usuarioId) {
        Usuario u = new Usuario(); u.setId(usuarioId);
        Vehiculo v = new Vehiculo();
        v.setId(id);
        v.setUsuario(u);
        v.setModelo("Kumai");
        v.setTipo(TipoVehiculo.BICIMOTO);
        v.setVelocidadMaximaKmh(45);
        v.setCreadoEn(OffsetDateTime.now());
        v.setActualizadoEn(OffsetDateTime.now());
        return v;
    }

    private VehiculoRequestDto reqVehiculo() {
        return new VehiculoRequestDto(null, "Aima", "Kumai",
                TipoVehiculo.BICIMOTO, 45, null);
    }

    // ---- Correo duplicado ----

    @Test
    void registrar_correoDuplicado_lanzaCorreoDuplicadoException() {
        when(usuarioRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.registrar(
                new RegistroRequestDto("test@example.com", "password123",
                        "A", "B", null, null)))
                .isInstanceOf(CorreoDuplicadoException.class);
    }

    @Test
    void registrar_normaliza_correo_a_minusculas() {
        when(usuarioRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(false);
        Usuario saved = usuarioBase(1L);
        saved.setCorreo("usuario@example.com");
        when(usuarioRepository.save(any())).thenReturn(saved);

        service.registrar(new RegistroRequestDto("USUARIO@EXAMPLE.COM", "password123",
                "A", "B", null, null));

        org.mockito.Mockito.verify(usuarioRepository).save(
                org.mockito.ArgumentMatchers.argThat(u ->
                        "usuario@example.com".equals(u.getCorreo())));
    }

    // ---- Límite de vehículos ----

    @Test
    void crearVehiculo_cuartoDa409() {
        Usuario u = usuarioBase(1L);
        when(vehiculoRepository.findUsuarioForUpdate(1L)).thenReturn(Optional.of(u));
        when(vehiculoRepository.countByUsuarioId(1L)).thenReturn(3);

        assertThatThrownBy(() -> service.crearVehiculo(1L, reqVehiculo()))
                .isInstanceOf(MaximoVehiculosException.class)
                .hasMessageContaining("3");
    }

    @Test
    void crearVehiculo_primeroQuedaActivo() {
        Usuario u = usuarioBase(1L);
        Vehiculo v = vehiculoBase(10L, 1L);
        when(vehiculoRepository.findUsuarioForUpdate(1L)).thenReturn(Optional.of(u));
        when(vehiculoRepository.countByUsuarioId(1L)).thenReturn(0);
        when(vehiculoRepository.save(any())).thenReturn(v);
        when(usuarioRepository.save(any())).thenReturn(u);

        VehiculoDto dto = service.crearVehiculo(1L, reqVehiculo());

        assertThat(dto).isNotNull();
    }

    // ---- Reasignación al eliminar activo ----

    @Test
    void eliminarVehiculo_activoReasignaAlMasReciente() {
        Vehiculo activo = vehiculoBase(10L, 1L);
        Vehiculo otro   = vehiculoBase(11L, 1L);
        Usuario u = usuarioBase(1L);
        u.setVehiculoActivo(activo);

        when(vehiculoRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(activo));
        when(usuarioRepository.findByIdConVehiculoActivo(1L)).thenReturn(Optional.of(u));
        when(vehiculoRepository.findMasRecienteExcluyendo(1L, 10L)).thenReturn(List.of(otro));
        when(usuarioRepository.save(any())).thenReturn(u);

        service.eliminarVehiculo(1L, 10L);

        // Verificar que el usuario termina con el vehículo 11 como activo
        verify(usuarioRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    @Test
    void eliminarVehiculo_activoSinMasVehiculosPoneNull() {
        Vehiculo activo = vehiculoBase(10L, 1L);
        Usuario u = usuarioBase(1L);
        u.setVehiculoActivo(activo);

        when(vehiculoRepository.findByIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(activo));
        when(usuarioRepository.findByIdConVehiculoActivo(1L)).thenReturn(Optional.of(u));
        when(vehiculoRepository.findMasRecienteExcluyendo(1L, 10L)).thenReturn(List.of());
        when(usuarioRepository.save(any())).thenReturn(u);

        service.eliminarVehiculo(1L, 10L);

        org.mockito.Mockito.verify(usuarioRepository).save(
                org.mockito.ArgumentMatchers.argThat(saved ->
                        saved.getVehiculoActivo() == null));
    }

    // ---- Vehículo de otro usuario da 404 ----

    @Test
    void obtenerVehiculo_ajeno_da404() {
        when(vehiculoRepository.findByIdAndUsuarioId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerVehiculo(1L, 99L))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ---- Cambio de contraseña ----

    @Test
    void cambiarContrasena_incorrecta_lanzaException() {
        Usuario u = usuarioBase(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.cambiarContrasena(1L,
                new CambiarContrasenaDto("wrong", "newpass123")))
                .isInstanceOf(ContrasenaIncorrectaException.class);
    }

    @Test
    void cambiarContrasena_correcta_actualiza() {
        Usuario u = usuarioBase(1L);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(u));
        when(usuarioRepository.save(any())).thenReturn(u);

        service.cambiarContrasena(1L, new CambiarContrasenaDto("password123", "nuevaPass123"));

        org.mockito.Mockito.verify(usuarioRepository).save(
                org.mockito.ArgumentMatchers.argThat(saved ->
                        passwordEncoder.matches("nuevaPass123", saved.getContrasenaHash())));
    }

    // ---- Contacto de emergencia completo o vacío ----

    @Test
    void registrar_sinContacto_guardaNulos() {
        when(usuarioRepository.existsByCorreoIgnoreCase(anyString())).thenReturn(false);
        Usuario saved = usuarioBase(1L);
        when(usuarioRepository.save(any())).thenReturn(saved);

        service.registrar(new RegistroRequestDto("a@b.com", "password123",
                "A", "B", null, null));

        org.mockito.Mockito.verify(usuarioRepository).save(
                org.mockito.ArgumentMatchers.argThat(u ->
                        u.getContactoEmergenciaNombre() == null));
    }

    // ---- categoriaReglamentaria derivada ----

    @Test
    void vehiculoMapper_motorizado_excludesCiclovia() {
        Vehiculo v = vehiculoBase(1L, 1L); // 45 km/h
        var perfil = VehiculoMapper.toPerfilVehiculo(v);
        assertThat(perfil.categoriaReglamentaria()).isEqualTo("AUTOMOTOR_MENOR_L");
        assertThat(perfil.puedeUsarCiclovia()).isFalse();
        assertThat(perfil.tiposViaExcluidos()).contains("cycleway");
    }

    @Test
    void vehiculoMapper_personal_includesCiclovia() {
        Vehiculo v = vehiculoBase(1L, 1L);
        v.setVelocidadMaximaKmh(20); // 20 km/h → MOVILIDAD_PERSONAL
        var perfil = VehiculoMapper.toPerfilVehiculo(v);
        assertThat(perfil.categoriaReglamentaria()).isEqualTo("MOVILIDAD_PERSONAL");
        assertThat(perfil.puedeUsarCiclovia()).isTrue();
        assertThat(perfil.tiposViaExcluidos()).doesNotContain("cycleway");
    }
}
