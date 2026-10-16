
package com.commitcrew.thebakeway.application.ord;

import java.util.List;

import org.springframework.stereotype.Service;

import com.commitcrew.thebakeway.domain.menu.MenuItem;
import com.commitcrew.thebakeway.domain.menu.MenuItemRegistry;

@Service
public class MenuService {

    private final MenuItemRegistry menuItemRegistry;

    public MenuService(MenuItemRegistry menuItemRegistry) {
        this.menuItemRegistry = menuItemRegistry;
    }

    public List<MenuItem> getMenuItems() {
        return menuItemRegistry.findByAvailableTrue();
    }
}
