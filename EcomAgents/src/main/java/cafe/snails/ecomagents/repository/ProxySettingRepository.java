package cafe.snails.ecomagents.repository;

import cafe.snails.ecomagents.model.ProxySetting;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 系统代理配置数据访问层。
 */
public interface ProxySettingRepository extends JpaRepository<ProxySetting, Long> {
}
