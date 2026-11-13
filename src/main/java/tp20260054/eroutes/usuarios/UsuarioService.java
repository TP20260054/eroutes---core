package tp20260054.eroutes.usuarios;

import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tp20260054.eroutes.mapeado.exception.RecursoNoEncontradoException;
import tp20260054.eroutes.usuarios.dto.ActualizarUsuarioDto;
import tp20260054.eroutes.usuarios.dto.CambiarContrasenaDto;
import tp20260054.eroutes.usuarios.dto.ContactoEmergenciaDto;
import tp20260054.eroutes.usuarios.dto.RegistroRequestDto;
import tp20260054.eroutes.usuarios.dto.UsuarioDto;
import tp20260054.eroutes.usuarios.dto.VehiculoDto;
import tp20260054.eroutes.usuarios.dto.VehiculoRequestDto;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final VehiculoRepository vehiculoRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${eroutes.vehiculos.maximo-por-usuario:3}")
    private int maximoVehiculos;

    // ---- Registro ----

    @Transactional
    public Usuario registrar(RegistroRequestDto req) {
        String correoNorm = req.correo().trim().toLowerCase();
        if (usuarioRepository.existsByCorreoIgnoreCase(correoNorm)) {
            throw new CorreoDuplicadoException(
                    "El correo '" + correoNorm + "' ya está registrado");
        }

        Usuario u = new Usuario();
        u.setCorreo(correoNorm);
        u.setContrasenaHash(passwordEncoder.encode(req.contrasena()));
        u.setNombres(req.nombres().trim());
        u.setApellidos(req.apellidos().trim());
        u.setTelefono(req.telefono());
        u.setCreadoEn(OffsetDateTime.now());
        u.setActualizadoEn(OffsetDateTime.now());
        u = usuarioRepository.save(u);

        if (req.vehiculo() != null) {
            Vehiculo v = crearVehiculoParaUsuario(u, req.vehiculo());
            u.setVehiculoActivo(v);
            u = usuarioRepository.save(u);
        }
        return u;
    }

    // ---- Consulta ----

    @Transactional(readOnly = true)
    public UsuarioDto obtener(Long usuarioId) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        List<Vehiculo> vehiculos = vehiculoRepository.findByUsuarioIdOrderByCreadoEnAsc(usuarioId);
        Long activoId = u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null;
        return toDto(u, vehiculos, activoId);
    }

    // ---- Actualizar datos del usuario ----

    @Transactional
    public UsuarioDto actualizar(Long usuarioId, ActualizarUsuarioDto req) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        String correoNorm = req.correo().trim().toLowerCase();
        // Verificar unicidad solo si cambió el correo
        if (!correoNorm.equals(u.getCorreo())) {
            if (usuarioRepository.existsByCorreoIgnoreCase(correoNorm)) {
                throw new CorreoDuplicadoException(
                        "El correo '" + correoNorm + "' ya está registrado por otro usuario");
            }
            u.setCorreo(correoNorm);
        }

        u.setNombres(req.nombres().trim());
        u.setApellidos(req.apellidos().trim());
        u.setTelefono(req.telefono());

        // Contacto de emergencia: o ambos campos o ninguno
        ContactoEmergenciaDto contacto = req.contactoEmergencia();
        if (contacto != null) {
            u.setContactoEmergenciaNombre(contacto.nombre());
            u.setContactoEmergenciaTelefono(contacto.telefono());
        } else {
            u.setContactoEmergenciaNombre(null);
            u.setContactoEmergenciaTelefono(null);
        }
        u.setActualizadoEn(OffsetDateTime.now());
        usuarioRepository.save(u);

        List<Vehiculo> vehiculos = vehiculoRepository.findByUsuarioIdOrderByCreadoEnAsc(usuarioId);
        Long activoId = u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null;
        return toDto(u, vehiculos, activoId);
    }

    // ---- Contraseña ----

    @Transactional
    public void cambiarContrasena(Long usuarioId, CambiarContrasenaDto req) {
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        if (!passwordEncoder.matches(req.contrasenaActual(), u.getContrasenaHash())) {
            throw new ContrasenaIncorrectaException("La contraseña actual no es correcta");
        }
        u.setContrasenaHash(passwordEncoder.encode(req.contrasenaNueva()));
        u.setActualizadoEn(OffsetDateTime.now());
        usuarioRepository.save(u);
    }

    // ---- Vehículos ----

    @Transactional
    public VehiculoDto crearVehiculo(Long usuarioId, VehiculoRequestDto req) {
        // Bloqueo pesimista para evitar race condition con el límite
        Usuario u = vehiculoRepository.findUsuarioForUpdate(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        int total = vehiculoRepository.countByUsuarioId(usuarioId);
        if (total >= maximoVehiculos) {
            throw new MaximoVehiculosException(
                    "Solo puedes registrar hasta " + maximoVehiculos + " vehículos");
        }

        Vehiculo v = crearVehiculoParaUsuario(u, req);

        // Primer vehículo → queda activo
        if (total == 0) {
            u.setVehiculoActivo(v);
            u.setActualizadoEn(OffsetDateTime.now());
            usuarioRepository.save(u);
        }

        Long activoId = u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null;
        return VehiculoMapper.toDto(v, activoId);
    }

    @Transactional(readOnly = true)
    public List<VehiculoDto> listarVehiculos(Long usuarioId) {
        List<Vehiculo> lista = vehiculoRepository.findByUsuarioIdOrderByCreadoEnAsc(usuarioId);
        Long activoId = usuarioRepository.findByIdConVehiculoActivo(usuarioId)
                .map(u -> u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null)
                .orElse(null);
        return lista.stream().map(v -> VehiculoMapper.toDto(v, activoId)).toList();
    }

    @Transactional(readOnly = true)
    public VehiculoDto obtenerVehiculo(Long usuarioId, Long vehiculoId) {
        Vehiculo v = vehiculoRepository.findByIdAndUsuarioId(vehiculoId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "Vehículo no encontrado"));
        Long activoId = usuarioRepository.findByIdConVehiculoActivo(usuarioId)
                .map(u -> u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null)
                .orElse(null);
        return VehiculoMapper.toDto(v, activoId);
    }

    @Transactional
    public VehiculoDto actualizarVehiculo(Long usuarioId, Long vehiculoId, VehiculoRequestDto req) {
        Vehiculo v = vehiculoRepository.findByIdAndUsuarioId(vehiculoId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado"));
        aplicarRequest(v, req);
        v.setActualizadoEn(OffsetDateTime.now());
        vehiculoRepository.save(v);

        Long activoId = usuarioRepository.findByIdConVehiculoActivo(usuarioId)
                .map(u -> u.getVehiculoActivo() != null ? u.getVehiculoActivo().getId() : null)
                .orElse(null);
        return VehiculoMapper.toDto(v, activoId);
    }

    @Transactional
    public void eliminarVehiculo(Long usuarioId, Long vehiculoId) {
        Vehiculo v = vehiculoRepository.findByIdAndUsuarioId(vehiculoId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado"));
        Usuario u = usuarioRepository.findByIdConVehiculoActivo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        boolean eraActivo = u.getVehiculoActivo() != null
                && vehiculoId.equals(u.getVehiculoActivo().getId());

        // Quitar la referencia antes de eliminar para evitar TransientPropertyValueException
        if (eraActivo) {
            u.setVehiculoActivo(null);
            usuarioRepository.save(u);
        }

        vehiculoRepository.delete(v);
        vehiculoRepository.flush();

        if (eraActivo) {
            List<Vehiculo> restantes = vehiculoRepository.findMasRecienteExcluyendo(usuarioId, vehiculoId);
            if (!restantes.isEmpty()) {
                u.setVehiculoActivo(restantes.get(0));
                u.setActualizadoEn(OffsetDateTime.now());
                usuarioRepository.save(u);
            }
        }
    }

    @Transactional
    public void activarVehiculo(Long usuarioId, Long vehiculoId) {
        Vehiculo v = vehiculoRepository.findByIdAndUsuarioId(vehiculoId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Vehículo no encontrado"));
        Usuario u = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        u.setVehiculoActivo(v);
        u.setActualizadoEn(OffsetDateTime.now());
        usuarioRepository.save(u);
    }

    @Transactional(readOnly = true)
    public tp20260054.eroutes.vehiculo.PerfilVehiculo perfilVehiculoActivo(Long usuarioId) {
        Usuario u = usuarioRepository.findByIdConVehiculoActivo(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        Vehiculo v = u.getVehiculoActivo();
        if (v == null) {
            throw new RecursoNoEncontradoException(
                    "El usuario no tiene un vehículo activo. Registra un vehículo primero.");
        }
        return VehiculoMapper.toPerfilVehiculo(v);
    }

    /** @deprecated Usar {@link VehiculoMapper#toPerfilVehiculo(Vehiculo)}. */
    static tp20260054.eroutes.vehiculo.PerfilVehiculo vehiculoAPerfilVehiculo(Vehiculo v) {
        return VehiculoMapper.toPerfilVehiculo(v);
    }

    // ---- Helpers ----

    private Vehiculo crearVehiculoParaUsuario(Usuario u, VehiculoRequestDto req) {
        Vehiculo v = new Vehiculo();
        v.setUsuario(u);
        aplicarRequest(v, req);
        v.setCreadoEn(OffsetDateTime.now());
        v.setActualizadoEn(OffsetDateTime.now());
        return vehiculoRepository.save(v);
    }

    private void aplicarRequest(Vehiculo v, VehiculoRequestDto req) {
        v.setAlias(req.alias());
        v.setMarca(req.marca());
        v.setModelo(req.modelo().trim());
        v.setTipo(req.tipo());
        v.setVelocidadMaximaKmh(req.velocidadMaximaKmh());
        v.setPlaca(req.placa() != null ? req.placa().toUpperCase() : null);
    }

    private UsuarioDto toDto(Usuario u, List<Vehiculo> vehiculos, Long activoId) {
        ContactoEmergenciaDto contacto = null;
        if (u.getContactoEmergenciaNombre() != null) {
            contacto = new ContactoEmergenciaDto(
                    u.getContactoEmergenciaNombre(),
                    u.getContactoEmergenciaTelefono());
        }
        List<VehiculoDto> vehiculoDtos = vehiculos.stream()
                .map(v -> VehiculoMapper.toDto(v, activoId))
                .toList();
        return new UsuarioDto(u.getId(), u.getCorreo(), u.getNombres(), u.getApellidos(),
                u.getTelefono(), contacto, activoId, vehiculoDtos);
    }
}
